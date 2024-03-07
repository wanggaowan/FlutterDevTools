package com.wanggaowan.tools.extensions.findusage

import com.intellij.find.FindBundle
import com.intellij.find.FindSettings
import com.intellij.find.findUsages.*
import com.intellij.ide.impl.DataManagerImpl
import com.intellij.navigation.NavigationItem
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.psi.*
import com.intellij.psi.search.*
import com.intellij.usageView.UsageInfo
import com.intellij.usageView.UsageViewContentManager
import com.intellij.usages.*
import com.intellij.usages.impl.UsageViewManagerImpl
import com.intellij.usages.similarity.clustering.ClusteringSearchSession
import com.intellij.util.ArrayUtil
import com.intellij.util.CommonProcessors
import com.intellij.util.Processor
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Supplier

/**
 * 查找文件使用位置工具,代码基本参考[FindUsagesManager],[UsageViewManagerImpl],SearchForUsagesRunnable
 *
 * @author Created by wanggaowan on 2024/3/6 10:30
 */
class FindUsageManager(val project: Project) {

    fun findUsages(
        psiElement: PsiElement,
        searchScope: SearchScope? = null,
        findProgress: FindProgress,
    ) {
        ApplicationManager.getApplication().assertIsDispatchThread()
        val handler: FindUsagesHandler? = FindUsagesManager(psiElement.project).getFindUsagesHandler(
            psiElement,
            FindUsagesHandlerFactory.OperationMode.USAGES_WITH_DEFAULT_OPTIONS
        )

        if (handler != null) {
            val dialog = handler.getFindUsagesDialog(false, this.shouldOpenInNewTab(), this.mustOpenInNewTab())
            dialog.close(DialogWrapper.OK_EXIT_CODE)

            val findUsagesOptions = dialog.calcFindUsagesOptions()
            if (searchScope != null) {
                findUsagesOptions.searchScope = searchScope
            }
            startFindUsages(findUsagesOptions, handler, findProgress)
        } else {
            findProgress.cancel()
        }
    }

    private fun startFindUsages(
        findUsagesOptions: FindUsagesOptions,
        handler: FindUsagesHandler,
        findProgress: FindProgress,
    ) {
        ApplicationManager.getApplication().assertIsDispatchThread()
        LOG.assertTrue(handler.psiElement.isValid)
        val primaryElements = handler.primaryElements
        checkNotNull(primaryElements, handler, "getPrimaryElements()")
        val secondaryElements = handler.secondaryElements
        checkNotNull(secondaryElements, handler, "getSecondaryElements()")
        doFindUsages(
            primaryElements,
            secondaryElements,
            handler,
            findUsagesOptions,
            findProgress
        )
    }

    private fun checkNotNull(elements: Array<PsiElement?>, handler: FindUsagesHandler, methodName: String) {
        for (index in elements.indices) {
            val element = elements[index]
            if (element == null) {
                LOG.error(
                    "$handler.$methodName has returned array with null elements: " + elements.toList()
                )
            }
        }
    }

    private fun doFindUsages(
        primaryElements: Array<PsiElement>,
        secondaryElements: Array<PsiElement>,
        handler: FindUsagesHandlerBase,
        findUsagesOptions: FindUsagesOptions,
        findProgress: FindProgress,
    ) {
        if (primaryElements.isEmpty()) {
            throw AssertionError("$handler $findUsagesOptions")
        } else {
            val primaryTargets =
                convertToUsageTargets(primaryElements, findUsagesOptions)
            val secondaryTargets =
                convertToUsageTargets(secondaryElements, findUsagesOptions)
            val targets = ArrayUtil.mergeArrays(primaryTargets, secondaryTargets)
            val searcher = createUsageSearcher(
                primaryTargets,
                secondaryTargets,
                handler,
                findUsagesOptions,
            )

            val scopeSupplier = getMaxSearchScopeToWarnOfFallingOutOf(targets)
            val searchScopeToWarnOfFallingOutOf = scopeSupplier.get()
            ProgressManager.getInstance().run(object : Task.Backgroundable(project, "", true) {
                private val myUsageCountWithoutDefinition = AtomicInteger(0)

                override fun run(indicator: ProgressIndicator) {
                    findProgress.start()
                    try {
                        searcher.generate {
                            if (!UsageViewManagerImpl.isInScope(it, searchScopeToWarnOfFallingOutOf)) {
                                return@generate true
                            }

                            if (UsageViewManager.isSelfUsage(it, targets)) {
                                return@generate true
                            }

                            val count = myUsageCountWithoutDefinition.incrementAndGet()
                            findProgress.find(it)
                            if (count > 1000) {
                                indicator.cancel()
                            }
                            return@generate true
                        }
                        findProgress.end(indicator)
                    } catch (e: Exception) {
                        // 搜索被取消
                        findProgress.cancel()
                    }
                }
            })
        }
    }

    private fun getMaxSearchScopeToWarnOfFallingOutOf(searchFor: Array<out UsageTarget>): Supplier<SearchScope> {
        val target = if (searchFor.isNotEmpty()) searchFor[0] else null
        val dataProvider = DataManagerImpl.getDataProviderEx(target)
        val scope = if (dataProvider != null) UsageView.USAGE_SCOPE.getData(dataProvider) else null
        if (scope != null) {
            return Supplier { scope }
        } else {
            val bgtProvider =
                if (dataProvider != null) PlatformCoreDataKeys.BGT_DATA_PROVIDER.getData(dataProvider) else null
            return Supplier<SearchScope> {
                val scope2 =
                    if (bgtProvider != null) UsageView.USAGE_SCOPE.getData(bgtProvider) else null
                (scope2 ?: GlobalSearchScope.everythingScope(project))
            }
        }
    }

    private fun shouldOpenInNewTab(): Boolean {
        return mustOpenInNewTab() || FindSettings.getInstance().isShowResultsInSeparateView
    }

    private fun mustOpenInNewTab(): Boolean {
        val selectedContent = UsageViewContentManager.getInstance(project).getSelectedContent(true)
        return selectedContent != null && selectedContent.isPinned
    }

    private fun convertToUsageTargets(
        elementsToSearch: Array<PsiElement>,
        findUsagesOptions: FindUsagesOptions
    ): Array<PsiElement2UsageTargetAdapter> {
        return elementsToSearch.map {
            convertToUsageTarget(it, findUsagesOptions)
        }.toTypedArray()
    }

    private fun convertToUsageTarget(
        elementToSearch: PsiElement,
        findUsagesOptions: FindUsagesOptions
    ): PsiElement2UsageTargetAdapter {
        if (elementToSearch is NavigationItem) {
            return PsiElement2UsageTargetAdapter(elementToSearch, findUsagesOptions, false)
        } else {
            throw IllegalArgumentException("Wrong usage target: ${elementToSearch}; ${elementToSearch::class.java}")
        }
    }

    @Throws(PsiInvalidElementAccessException::class)
    private fun createUsageSearcher(
        primaryTargets: Array<PsiElement2UsageTargetAdapter>,
        secondaryTargets: Array<PsiElement2UsageTargetAdapter>,
        handler: FindUsagesHandlerBase,
        options: FindUsagesOptions,
    ): UsageSearcher {
        ReadAction.run<RuntimeException> {
            primaryTargets.forEach {
                val element = it.element
                if (element == null || !element.isValid) {
                    throw PsiInvalidElementAccessException(element)
                }
            }

            secondaryTargets.forEach {
                val element = it.element
                if (element == null || !element.isValid) {
                    throw PsiInvalidElementAccessException(element)
                }
            }
        }

        val optionsClone = options.clone()
        return UsageSearcher { processor: Processor<in Usage?> ->
            val project = ReadAction.compute<Project, RuntimeException> {
                primaryTargets[0].project
            }

            val indicator = ProgressManager.getInstance().progressIndicator
            LOG.assertTrue(indicator != null, "Must run under progress. see ProgressManager.run*")
            runUpdate(primaryTargets, indicator)
            runUpdate(secondaryTargets, indicator)
            val primaryElements =
                ReadAction.compute<Array<PsiElement>, RuntimeException> {
                    PsiElement2UsageTargetAdapter.convertToPsiElements(primaryTargets)
                }
            val secondaryElements =
                ReadAction.compute<Array<PsiElement>, RuntimeException> {
                    PsiElement2UsageTargetAdapter.convertToPsiElements(secondaryTargets)
                }

            val clusteringSearchSession =
                ClusteringSearchSession.createClusteringSessionIfEnabled()
            val usageInfoProcessor = CommonProcessors.UniqueProcessor { usageInfo: UsageInfo ->
                val usage = ReadAction.compute(
                    ThrowableComputable {
                        if (clusteringSearchSession != null) UsageInfoToUsageConverter.convertToSimilarUsage(
                            primaryElements,
                            usageInfo,
                            clusteringSearchSession
                        ) else UsageInfoToUsageConverter.convert(primaryElements, usageInfo)
                    })
                processor.process(usage)
            }
            val elements = ArrayUtil.mergeArrays(
                primaryElements,
                secondaryElements,
                PsiElement.ARRAY_FACTORY
            )

            optionsClone.fastTrack = SearchRequestCollector(SearchSession(*elements))
            if (optionsClone.searchScope is GlobalSearchScope) {
                optionsClone.searchScope =
                    optionsClone.searchScope.union(GlobalSearchScope.projectScope(project))
            }

            try {
                for (element in elements) {
                    if (!handler.processElementUsages(element, usageInfoProcessor, optionsClone)) {
                        return@UsageSearcher
                    }

                    val iterator: Iterator<CustomUsageSearcher> = CustomUsageSearcher.EP_NAME.extensionList.iterator()
                    while (iterator.hasNext()) {
                        val searcher = iterator.next()
                        try {
                            searcher.processElementUsages(element, processor, optionsClone)
                        } catch (var25: IndexNotReadyException) {
                            DumbService.getInstance(element.project).showDumbModeNotification(
                                FindBundle.message("notification.find.usages.is.not.available.during.indexing")
                            )
                        } catch (e: ProcessCanceledException) {
                            throw e
                        } catch (e: Exception) {
                            LOG.error(e)
                        }
                        ProgressManager.checkCanceled()
                    }
                    ProgressManager.checkCanceled()
                }

                PsiSearchHelper.getInstance(project).processRequests(optionsClone.fastTrack) { ref: PsiReference ->
                    ProgressManager.checkCanceled()
                    val info = ReadAction.compute<UsageInfo?, RuntimeException> {
                        if (!ref.element.isValid) null else UsageInfo(ref)
                    }
                    info == null || usageInfoProcessor.process(info)
                }
            } finally {
                optionsClone.fastTrack = null
            }
        }
    }

    private fun runUpdate(targets: Array<PsiElement2UsageTargetAdapter>, indicator: ProgressIndicator) {
        for (index in targets.indices) {
            val target = targets[index]
            indicator.modalityState
            indicator.checkCanceled()
            ReadAction.run<RuntimeException> {
                target.update()
            }
        }
    }

    companion object {
        private val LOG = Logger.getInstance(FindUsageManager::class.java)
    }
}

/**
 * 查找进程
 */
abstract class FindProgress {
    /**
     * 开始查找
     */
    open fun start() {

    }

    /**
     * 查找到内容
     */
    abstract fun find(usage: Usage)

    /**
     * 查找结束
     */
    open fun end(indicator: ProgressIndicator) {

    }

    /**
     * 查找取消
     */
    open fun cancel() {

    }
}
