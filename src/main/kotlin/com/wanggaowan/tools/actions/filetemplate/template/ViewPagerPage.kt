package com.wanggaowan.tools.actions.filetemplate.template

import com.wanggaowan.tools.actions.filetemplate.TemplateChildEntity
import com.wanggaowan.tools.actions.filetemplate.TemplateEntity

object ViewPagerPage {

    val template: TemplateEntity
        get() {
            val template = TemplateEntity()
            template.name = "viewPagerPage"

            val children = arrayListOf<TemplateChildEntity>()
            template.children = children

            val view = TemplateChildEntity()
            view.name = "view"
            view.content = viewContent
            children.add(view)

            val controller = TemplateChildEntity()
            controller.name = "controller"
            controller.content = controllerContent
            children.add(controller)

            val state = TemplateChildEntity()
            state.name = "state"
            state.content = stateContent
            children.add(state)
            return template
        }

    private val viewContent = """
import 'package:flutter/material.dart';
import 'package:get/get.dart';
import 'package:kq_flutter_widgets/getx/kq_get_builder.dart';
import 'package:kq_flutter_widgets/widgets/emptyView/empty_view.dart';
import 'package:kq_flutter_widgets/widgets/tabbar/kq_tab_bar.dart';
import 'package:kq_flutter_widgets/widgets/tabbar/kq_tab_bar_page_view.dart';
import 'package:kq_flutter_widgets/widgets/titleBar/kq_title_bar.dart';

import 'controller.dart';

/// ${'$'}desc${'$'}
/// create by ${'$'}USER${'$'} ${'$'}DATETIME${'$'}
class ${'$'}pageName${'$'}Page extends StatefulWidget {
  const ${'$'}pageName${'$'}Page({super.key});

  @override
  State<${'$'}pageName${'$'}Page> createState() => _${'$'}pageName${'$'}PageState();
}

class _${'$'}pageName${'$'}PageState extends State<${'$'}pageName${'$'}Page> {
  final _controller = ${'$'}pageName${'$'}Controller();

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: KqHeadBar(
        // todo 填写界面标题
        headTitle: '',
        back: Get.back,
      ),
      body: KqGetBuilder(
        init: _controller,
        global: false,
        builder: (controller) => _buildBody(context),
      ),
    );
  }

  Widget _buildBody(BuildContext context) {
    if (_controller.state.firstRequest) {
      return const SizedBox();
    }

    if (_controller.state.isDataEmpty) {
      return const KqEmptyView(forceSliver: false, autoSliver: false);
    }

    return Column(
      children: [
        KqTabBar(
          tabController: _controller.tabController,
          items: [
            // todo 初始化标签页
          ],
        ),
        Expanded(
          child: KqTabBarView(
            controller: _controller.tabController,
            children: [
              // todo 初始化标签页对应的界面
            ],
          ),
        )
      ],
    );
  }
}     
""".trimIndent()

    private val controllerContent = """
import 'package:flutter/material.dart';
import 'package:kq_flutter_widgets/getx/kq_get_builder.dart';

import 'state.dart';

class ${'$'}pageName${'$'}Controller extends KqGetXController {
  final ${'$'}pageName${'$'}State state = ${'$'}pageName${'$'}State();
  TabController? _tabController;

  TabController get tabController =>
      _tabController ??= TabController(length: state.pageCount, vsync: vsync);
}  
""".trimIndent()

    private val stateContent = """
class ${'$'}pageName${'$'}State {
  ${'$'}pageName${'$'}State() {
    ///Initialize variables
  }

  bool firstRequest = true;
  
  bool get isDataEmpty => true;
  
  int get pageCount => 0;
}
""".trimIndent()
}


