package com.Evaluate.controller;


import com.Evaluate.dto.Result;
import com.Evaluate.service.IShopTypeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;


@RestController
@RequestMapping("/shop-type")
public class ShopTypeController {
    @Resource
    private IShopTypeService typeService;

    @GetMapping("list")
    //todo 给店铺类型做缓存，实现类型的排序等
    public Result queryTypeList() {
       return typeService.queryTypeList();
    }

}
