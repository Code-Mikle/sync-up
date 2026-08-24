package com.mikle.syncup.controller;

import com.mikle.syncup.common.BaseResponse;
import com.mikle.syncup.common.ResultUtils;
import com.mikle.syncup.model.vo.TagCategoryVO;
import com.mikle.syncup.service.TagService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/tag")
public class TagController {

    @Resource
    private TagService tagService;

    @GetMapping("/list")
    public BaseResponse<List<TagCategoryVO>> listEnabledTags() {
        return ResultUtils.success(tagService.listEnabledCategories());
    }
}
