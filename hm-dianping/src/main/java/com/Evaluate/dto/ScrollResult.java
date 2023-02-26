package com.Evaluate.dto;

import lombok.Data;

import java.util.List;

/**
 * 滚动分页的结果
 */
@Data
public class ScrollResult {
    //todo 分页的内容，使用泛型
    private List<?> list;
    //最小时间
    private Long minTime;
    //偏移量
    private Integer offset;
}
