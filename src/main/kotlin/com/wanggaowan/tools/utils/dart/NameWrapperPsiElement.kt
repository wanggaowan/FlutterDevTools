package com.wanggaowan.tools.utils.dart

import com.intellij.lang.ASTNode
import com.intellij.lang.Language
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.SearchScope
import javax.swing.Icon

/**
 * [PsiElement]包装类，主要是改变文本值
 *
 * @author Created by wanggaowan on 2024/4/8 16:19
 */
class NameWrapperPsiElement(private val node: PsiElement, private val getName: (PsiElement) -> String) : PsiElement {
    override fun <T : Any?> getUserData(p0: Key<T>): T? {
        return node.getUserData(p0)
    }

    override fun <T : Any?> putUserData(p0: Key<T>, p1: T?) {
        return node.putUserData(p0, p1)
    }

    override fun getIcon(p0: Int): Icon {
        return node.getIcon(p0)
    }

    override fun getProject(): Project {
        return node.project
    }

    override fun getLanguage(): Language {
        return node.language
    }

    override fun getManager(): PsiManager {
        return node.manager
    }

    override fun getChildren(): Array<PsiElement> {
        return node.children
    }

    override fun getParent(): PsiElement {
        return node.parent
    }

    override fun getFirstChild(): PsiElement {
        return node.firstChild
    }

    override fun getLastChild(): PsiElement {
        return node.lastChild
    }

    override fun getNextSibling(): PsiElement {
        return node.lastChild
    }

    override fun getPrevSibling(): PsiElement {
        return node.prevSibling
    }

    override fun getContainingFile(): PsiFile {
        return node.containingFile
    }

    override fun getTextRange(): TextRange {
        return node.textRange
    }

    override fun getStartOffsetInParent(): Int {
        return node.startOffsetInParent
    }

    override fun getTextLength(): Int {
        return node.textLength
    }

    override fun findElementAt(p0: Int): PsiElement? {
        return node.findElementAt(p0)
    }

    override fun findReferenceAt(p0: Int): PsiReference? {
        return node.findReferenceAt(p0)
    }

    override fun getTextOffset(): Int {
        return node.textOffset
    }

    override fun getText(): String {
        return getName(node)
    }

    override fun textToCharArray(): CharArray {
        return node.textToCharArray()
    }

    override fun getNavigationElement(): PsiElement {
        return node.navigationElement
    }

    override fun getOriginalElement(): PsiElement {
        return node.originalElement
    }

    override fun textMatches(p0: CharSequence): Boolean {
        return node.textMatches(p0)
    }

    override fun textMatches(p0: PsiElement): Boolean {
        return node.textMatches(p0)
    }

    override fun textContains(p0: Char): Boolean {
        return node.textContains(p0)
    }

    override fun accept(p0: PsiElementVisitor) {
        return node.accept(p0)
    }

    override fun acceptChildren(p0: PsiElementVisitor) {
        return node.acceptChildren(p0)
    }

    override fun copy(): PsiElement {
        return node.copy()
    }

    override fun add(p0: PsiElement): PsiElement {
        return node.add(p0)
    }

    override fun addBefore(p0: PsiElement, p1: PsiElement?): PsiElement {
        return node.addBefore(p0, p1)
    }

    override fun addAfter(p0: PsiElement, p1: PsiElement?): PsiElement {
        return node.addAfter(p0, p1)
    }

    @Deprecated("Deprecated in Java")
    override fun checkAdd(p0: PsiElement) {
        return node.checkAdd(p0)
    }

    override fun addRange(p0: PsiElement?, p1: PsiElement?): PsiElement {
        return node.addRange(p0, p1)
    }

    override fun addRangeBefore(p0: PsiElement, p1: PsiElement, p2: PsiElement?): PsiElement {
        return node.addRangeBefore(p0, p1, p2)
    }

    override fun addRangeAfter(p0: PsiElement?, p1: PsiElement?, p2: PsiElement?): PsiElement {
        return node.addRangeAfter(p0, p1, p2)
    }

    override fun delete() {
        return node.delete()
    }

    @Deprecated("Deprecated in Java")
    override fun checkDelete() {
        return node.checkDelete()
    }

    override fun deleteChildRange(p0: PsiElement?, p1: PsiElement?) {
        node.deleteChildRange(p0, p1)
    }

    override fun replace(p0: PsiElement): PsiElement {
        return node.replace(p0)
    }

    override fun isValid(): Boolean {
        return node.isValid
    }

    override fun isWritable(): Boolean {
        return node.isWritable
    }

    override fun getReference(): PsiReference? {
        return node.reference
    }

    override fun getReferences(): Array<PsiReference> {
        return node.references
    }

    override fun <T : Any?> getCopyableUserData(p0: Key<T>): T? {
        return node.getCopyableUserData(p0)
    }

    override fun <T : Any?> putCopyableUserData(p0: Key<T>, p1: T?) {
        node.putCopyableUserData(p0, p1)
    }

    override fun processDeclarations(p0: PsiScopeProcessor,
                                     p1: ResolveState,
                                     p2: PsiElement?,
                                     p3: PsiElement): Boolean {
        return node.processDeclarations(p0, p1, p2, p3)
    }

    override fun getContext(): PsiElement? {
        return node.context
    }

    override fun isPhysical(): Boolean {
        return node.isPhysical
    }

    override fun getResolveScope(): GlobalSearchScope {
        return node.resolveScope
    }

    override fun getUseScope(): SearchScope {
        return node.useScope
    }

    override fun getNode(): ASTNode {
        return node.node
    }

    override fun isEquivalentTo(p0: PsiElement?): Boolean {
        return node.isEquivalentTo(p0)
    }
}
