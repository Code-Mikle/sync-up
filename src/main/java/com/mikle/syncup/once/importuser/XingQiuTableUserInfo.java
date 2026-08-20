package com.mikle.syncup.once.importuser;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

/**
 * 星球表格用户信息
 */
@Data
public class XingQiuTableUserInfo {

    /**
     * 用户昵称
     */
    @ExcelProperty("成员昵称")
    private String username;

}
