package com.wanggaowan.tools.listener

import java.awt.event.ComponentEvent
import java.awt.event.ComponentListener

/**
 * [ComponentListener]的空实现
 *
 * @author Created by wanggaowan on 2023/2/10 13:09
 */
open class SimpleComponentListener : ComponentListener {
    override fun componentResized(p0: ComponentEvent?) {
    }

    override fun componentMoved(p0: ComponentEvent?) {
    }

    override fun componentShown(p0: ComponentEvent?) {
    }

    override fun componentHidden(p0: ComponentEvent?) {
    }
}
