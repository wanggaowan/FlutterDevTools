package com.wanggaowan.tools.actions.filetemplate.template

import com.wanggaowan.tools.actions.filetemplate.Template
import com.wanggaowan.tools.actions.filetemplate.TemplateChild

object SimplePage {
    val template: Template
        get() {
            val template = Template()
            template.name = "simplePage"

            val children = arrayListOf<TemplateChild>()
            template.children = children

            val view = TemplateChild()
            view.name = "view"
            view.content = viewContent
            children.add(view)

            val controller = TemplateChild()
            controller.name = "controller"
            controller.content = controllerContent
            children.add(controller)

            return template
        }

    private val viewContent = """
import 'package:flutter/material.dart';
import 'package:get/get.dart';
import 'package:kq_flutter_widgets/getx/kq_get_builder.dart';
import 'package:kq_flutter_widgets/widgets/titleBar/kq_title_bar.dart';

import 'controller.dart';

class ${'$'}pageName${'$'}Page extends StatefulWidget {
  const TestPage({super.key});

  @override
  State<TestPage> createState() => _TestPageState();
}

class _TestPageState extends State<TestPage> {
  final _controller = ${'$'}pageName${'$'}Controller();

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: KqHeadBar(
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
    return const Placeholder();
  }
}            
""".trimIndent()

    private val controllerContent = """
import 'package:kq_flutter_widgets/getx/kq_get_builder.dart';

import 'state.dart';

class ${'$'}pageName${'$'}Controller extends KqGetXController {
  final ${'$'}pageName${'$'}State state = ${'$'}pageName${'$'}State();

}    

class ${'$'}pageName${'$'}State {

}  
""".trimIndent()
}
