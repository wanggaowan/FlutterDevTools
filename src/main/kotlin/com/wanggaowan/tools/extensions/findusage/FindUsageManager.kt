package com.wanggaowan.tools.extensions.findusage

import com.intellij.find.FindBundle
import com.intellij.find.FindSettings
import com.intellij.find.findUsages.*
import com.intellij.ide.impl.DataManagerImpl
import com.intellij.navigation.NavigationItem
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiInvalidElementAccessException
import com.intellij.psi.PsiReference
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

private val LOG = Logger.getInstance(FindUsageManager::class.java)

/**
 * 查找文件使用位置工具,代码基本参考[FindUsagesManager],[UsageViewManagerImpl],SearchForUsagesRunnable
 *
 * @author Created by wanggaowan on 2024/3/6 10:30
 */
class FindUsageManager(val project: Project) {

    /**
     * 查找给定[psiElements]被使用的地方
     *
     * [searchScope]指定查找范围
     *
     * [onlyFindOneUse]表示只查找一处使用，找到就结束当前元素的查找，继续对下一个元素进行查找。
     *
     * [progressTitle]为进度标题，参数为null时表示总进度标题，不会null则为查找具体PsiElement时的进度标题
     */
    fun findUsages(
        psiElements: Array<PsiElement>,
        searchScope: SearchScope? = null,
        onlyFindOneUse: Boolean = false,
        findProgress: FindProgress,
        progressTitle: ((psiElement: PsiElement?) -> String?)? = null
    ) {

        ProgressManager.getInstance()
            .run(object : Task.Backgroundable(project, progressTitle?.invoke(null) ?: "Find usages", true) {
                override fun run(indicator: ProgressIndicator) {
                    val myUsageCount = AtomicInteger(0)
                    val mySearchCount = AtomicInteger(-1)
                    findProgress.start(indicator)
                    val totalCount = psiElements.size
                    doSearch(
                        psiElements,
                        totalCount,
                        mySearchCount,
                        myUsageCount,
                        searchScope,
                        onlyFindOneUse,
                        findProgress,
                        progressTitle,
                        indicator
                    )
                    while (mySearchCount.get() < totalCount && !indicator.isCanceled) {
                        try {
                            Thread.sleep(50)
                        } catch (_: Exception) {
                            //
                        }
                    }

                    if (indicator.isCanceled) {
                        findProgress.cancel()
                    } else {
                        findProgress.end(indicator)
                    }
                }
            })
    }

    private fun doSearch(
        elements: Array<PsiElement>,
        totalCount: Int,
        mySearchCount: AtomicInteger,
        myUsageCount: AtomicInteger,
        searchScope: SearchScope?,
        onlyFindOneUse: Boolean = false,
        findProgress: FindProgress,
        progressTitle: ((psiElement: PsiElement?) -> String?)?,
        parentIndicator: ProgressIndicator
    ) {
        if (parentIndicator.isCanceled) {
            return
        }

        val index = mySearchCount.incrementAndGet()
        if (index < 0 || index >= totalCount) {
            return
        }

        WriteCommandAction.runWriteCommandAction(project) {
            val element = elements[index]
            val searcher = createSearcher(element, searchScope, onlyFindOneUse, findProgress)
            if (searcher == null) {
                doSearch(
                    elements,
                    totalCount,
                    mySearchCount,
                    myUsageCount,
                    searchScope,
                    onlyFindOneUse,
                    findProgress,
                    progressTitle,
                    parentIndicator
                )
                return@runWriteCommandAction
            }

            ProgressManager.getInstance()
                .run(object : Task.Backgroundable(project, progressTitle?.invoke(element) ?: "Find usages", true) {
                    override fun run(indicator: ProgressIndicator) {
                        try {
                            searcher.search(myUsageCount, parentIndicator)
                        } catch (_: Exception) {
                            // 搜索被取消
                        }

                        doSearch(
                            elements,
                            totalCount,
                            mySearchCount,
                            myUsageCount,
                            searchScope,
                            onlyFindOneUse,
                            findProgress,
                            progressTitle,
                            parentIndicator
                        )
                    }
                })
        }
    }

    /**
     * 查找给定[psiElement]被使用的地方
     *
     * [searchScope]指定查找范围
     *
     * [onlyFindOneUse]表示只查找一处使用，找到就结束当前元素的查找
     */
    fun findUsages(
        psiElement: PsiElement,
        searchScope: SearchScope? = null,
        onlyFindOneUse: Boolean = false,
        findProgress: FindProgress,
        progressTitle: String? = null
    ) {
        findUsages(arrayOf(psiElement), searchScope, onlyFindOneUse, findProgress) tag@{
            return@tag progressTitle
        }
    }

    /**
     * 创建查找[psiElement]使用位置查找器
     */
    private fun createSearcher(
        psiElement: PsiElement,
        searchScope: SearchScope? = null,
        onlyFindOneUse: Boolean,
        findProgress: FindProgress,
    ): Searcher? {
        ApplicationManager.getApplication().assertIsDispatchThread()
        val handler: FindUsagesHandler? = FindUsagesManager(psiElement.project).getFindUsagesHandler(
            psiElement,
            FindUsagesHandlerFactory.OperationMode.USAGES_WITH_DEFAULT_OPTIONS
        )

        if (handler != null) {
            // 采用dialog获取查找配置，会触发短时间提交多个读取操作异常

            // val dialog = handler.getFindUsagesDialog(false, this.shouldOpenInNewTab(), this.mustOpenInNewTab())
            // dialog.close(DialogWrapper.OK_EXIT_CODE)

            val findUsagesOptions = FindUsagesOptions(project)
            findUsagesOptions.isUsages = true
            findUsagesOptions.isSearchForTextOccurrences = false
            if (searchScope != null) {
                findUsagesOptions.searchScope = searchScope
            }
            return createSearcher(psiElement, findUsagesOptions, handler, onlyFindOneUse, findProgress)
        } else {
            return null
        }
    }

    private fun createSearcher(
        psiElement: PsiElement,
        findUsagesOptions: FindUsagesOptions,
        handler: FindUsagesHandler,
        onlyFindOneUse: Boolean,
        findProgress: FindProgress,
    ): Searcher {
        ApplicationManager.getApplication().assertIsDispatchThread()
        LOG.assertTrue(handler.psiElement.isValid)
        val primaryElements = handler.primaryElements
        checkNotNull(primaryElements, handler, "getPrimaryElements()")
        val secondaryElements = handler.secondaryElements
        checkNotNull(secondaryElements, handler, "getSecondaryElements()")
        return createSearcher(
            psiElement,
            primaryElements,
            secondaryElements,
            handler,
            findUsagesOptions,
            onlyFindOneUse,
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

    private fun createSearcher(
        psiElement: PsiElement,
        primaryElements: Array<PsiElement>,
        secondaryElements: Array<PsiElement>,
        handler: FindUsagesHandlerBase,
        findUsagesOptions: FindUsagesOptions,
        onlyFindOneUse: Boolean,
        findProgress: FindProgress,
    ): Searcher {
        if (primaryElements.isEmpty()) {
            throw AssertionError("$handler $findUsagesOptions")
        } else {
            val primaryTargets =
                convertToUsageTargets(primaryElements, findUsagesOptions)
            val secondaryTargets =
                convertToUsageTargets(secondaryElements, findUsagesOptions)
            val targets = ArrayUtil.mergeArrays(primaryTargets, secondaryTargets)
            val scopeSupplier = getMaxSearchScopeToWarnOfFallingOutOf(targets)
            val searchScopeToWarnOfFallingOutOf = scopeSupplier.get()
            return Searcher(
                project,
                psiElement, targets,
                primaryTargets,
                secondaryTargets,
                handler,
                findUsagesOptions,
                findProgress,
                searchScopeToWarnOfFallingOutOf,
                onlyFindOneUse
            )
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
}

class Searcher(
    private val project: Project,
    private val psiElement: PsiElement,
    private val targets: Array<out UsageTarget>,
    private val primaryTargets: Array<PsiElement2UsageTargetAdapter>,
    private val secondaryTargets: Array<PsiElement2UsageTargetAdapter>,
    private val handler: FindUsagesHandlerBase,
    private val options: FindUsagesOptions,
    private val findProgress: FindProgress,
    private val searchScopeToWarnOfFallingOutOf: SearchScope,
    private val onlyFindOneUse: Boolean
) {

    private var isStart = false
    private var isCancel = false

    fun search(usageCount: AtomicInteger, indicator: ProgressIndicator) {
        if (isStart) {
            return
        }

        isStart = true
        findProgress.startFindElement(indicator, psiElement)
        val everythingScope = GlobalSearchScope.everythingScope(project)
        createUsageSearcher().generate {
            if (!UsageViewManagerImpl.isInScope(it, searchScopeToWarnOfFallingOutOf, everythingScope)) {
                return@generate true
            }

            if (UsageViewManager.isSelfUsage(it, targets)) {
                return@generate true
            }

            val count = usageCount.incrementAndGet()
            if (count > 1000) {
                indicator.cancel()
                return@generate false
            }

            findProgress.find(psiElement, it)
            if (onlyFindOneUse) {
                cancel()
            }
            return@generate true
        }
        findProgress.endFindElement(indicator, psiElement)
    }

    @Throws(PsiInvalidElementAccessException::class)
    private fun createUsageSearcher(): UsageSearcher {
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
                    if (isCancel) {
                        return@UsageSearcher
                    }

                    if (!handler.processElementUsages(element, usageInfoProcessor, optionsClone)) {
                        return@UsageSearcher
                    }

                    val iterator: Iterator<CustomUsageSearcher> = CustomUsageSearcher.EP_NAME.extensionList.iterator()
                    while (iterator.hasNext()) {
                        if (isCancel) {
                            return@UsageSearcher
                        }

                        val searcher = iterator.next()
                        try {
                            searcher.processElementUsages(element, processor, optionsClone)
                        } catch (_: IndexNotReadyException) {
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
                    if (isCancel) {
                        return@processRequests false
                    }

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

    private fun cancel() {
        isCancel = true
    }
}

/**
 * 查找进程
 */
abstract class FindProgress {
    /**
     * 开始查找进程
     */
    open fun start(indicator: ProgressIndicator) {

    }

    /**
     * 开始查找[target]元素
     */
    open fun startFindElement(indicator: ProgressIndicator, target: PsiElement) {

    }

    /**
     * 查找到内容,[target]为当前查找的对象，[usage]为找到的用法
     */
    abstract fun find(target: PsiElement, usage: Usage)

    /**
     * [target]元素查找结束
     */
    open fun endFindElement(indicator: ProgressIndicator, target: PsiElement) {

    }

    /**
     * 查找进程结束
     */
    open fun end(indicator: ProgressIndicator) {

    }

    /**
     * 查找取消
     */
    open fun cancel() {

    }
}
