package com.wanggaowan.tools.actions.filetemplate.template

import com.wanggaowan.tools.actions.filetemplate.TemplateChildEntity
import com.wanggaowan.tools.actions.filetemplate.TemplateEntity

object SimpleFormPage {
    val template: TemplateEntity
        get() {
            val template = TemplateEntity()
            template.name = "simpleFormPage"

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

            return template
        }

    private val viewContent = """
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:get/get.dart';
import 'package:kq_flutter_core_widget/extentions/kq_extentions.dart';
import 'package:kq_flutter_core_widget/getx/kq_get_builder.dart';
import 'package:kq_flutter_core_widget/utils/kq_screen_util.dart';
import 'package:kq_flutter_widgets/utils/kq_form_util.dart';
import 'package:kq_flutter_widgets/widgets/button/kq_bottom_button.dart';
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
    return Column(
              children: [
                Expanded(
                    child: SingleChildScrollView(
                  keyboardDismissBehavior: Platform.isIOS
                      ? ScrollViewKeyboardDismissBehavior.onDrag
                      : ScrollViewKeyboardDismissBehavior.manual,
                  child: Column(
                    children: [
                      ...KqFormUtil.renderFormWidgetList(_controller.state.formData),
                      // 防止键盘下来时抖动
                      SizedBox(height: 50.r,)
                    ],
                  ),
                )),
                if (!context.keyboardVisible)
                  KqBottomButton(
                    // todo 填写提交按钮名称
                    title: '',
                    onTap: (disabled) {
                      _controller.submitData();
                    },
                  )
              ],
            );
  }
}            
""".trimIndent()

    private val controllerContent = """
import 'package:get/get.dart';
import 'package:kq_flutter_core_widget/getx/kq_get_builder.dart';
import 'package:kq_flutter_core_widget/network/response.dart';
import 'package:kq_flutter_widgets/utils/kq_form_util.dart';
import 'package:kq_flutter_widgets/widgets/formItem/entity/kq_form_entity.dart';

class #pageName#Controller extends KqGetXController {
  final #pageName#State state = #pageName#State();

  @override
  void onInit() {
    super.onInit();
    state.formData = [
      //todo 填充表单实体数据
      
    ];
    update();
  }
  
  /// 提交数据
  submitData() async {
    // 检查必填项
    if (KqFormUtil.checkMustInputForm(state.formData)) {
      return;
    }
    // 表单数据
    var params = KqFormUtil.getFormSubmitJson(state.formData);
    // 请求接口
    #apiName#Api.#methodName#(
      params,
      cancelToken: createCancelToken(),
      callback: (response) {
        if (response.code == ApiResponse.success) {
          // 关闭页面
          Get.back(result: true);
        }
      },
    );
  }
}  
  
class #pageName#State {
  #pageName#State() {
    ///Initialize variables
  }

  /// 表单实体
  List<KqFormEntity> formData = [];
}     
""".trimIndent()
}


