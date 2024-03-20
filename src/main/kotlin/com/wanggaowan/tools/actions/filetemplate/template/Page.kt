package com.wanggaowan.tools.actions.filetemplate.template

import com.wanggaowan.tools.actions.filetemplate.TemplateChildEntity
import com.wanggaowan.tools.actions.filetemplate.TemplateEntity

object Page {

    val template: TemplateEntity
        get() {
            val template = TemplateEntity()
            template.name = "page"

            val children = arrayListOf<TemplateChildEntity>()
            template.children = children

            val view = TemplateChildEntity()
            view.name = "view.dart"
            view.content = viewContent
            children.add(view)

            val controller = TemplateChildEntity()
            controller.name = "controller.dart"
            controller.content = controllerContent
            children.add(controller)

            val state = TemplateChildEntity()
            state.name = "state.dart"
            state.content = stateContent
            children.add(state)
            return template
        }

    private val viewContent = """
import 'package:flutter/material.dart';
import 'package:get/get.dart';
import 'package:kq_flutter_core_widget/getx/kq_get_builder.dart';
import 'package:kq_flutter_widgets/widgets/emptyView/empty_view.dart';
import 'package:kq_flutter_widgets/widgets/titleBar/kq_title_bar.dart';

import 'controller.dart';

/// #desc#
/// create by #USER# #DATETIME#
class #pageName#Page extends StatefulWidget {
  const TestPage({super.key});

  @override
  State<#pageName#Page> createState() => _#pageName#PageState();
}

class _#pageName#PageState extends State<#pageName#Page> {
  final _controller = #pageName#Controller();

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

    // todo 完善具体的界面逻辑
    return const Placeholder();
  }
}            
""".trimIndent()

    private val controllerContent = """
import 'package:kq_flutter_core_widget/getx/kq_get_builder.dart';

import 'state.dart';

class #pageName#Controller extends KqGetXController {
  final #pageName#State state = #pageName#State();
}    
""".trimIndent()

    private val stateContent = """
class #pageName#State {
  #pageName#State() {
    ///Initialize variables
  }

  bool firstRequest = true;
  
  bool get isDataEmpty => true;
}   
""".trimIndent()
}


