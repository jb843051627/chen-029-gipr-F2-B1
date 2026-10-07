package com.fc.v2.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.common.base.BaseController;
import com.fc.v2.common.domain.AjaxResult;
import com.fc.v2.common.domain.ResultTable;
import com.fc.v2.common.log.Log;
import com.fc.v2.model.auto.TGiprTermBook;
import com.fc.v2.service.ITGiprTermBookService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.*;

/**
 * 名录对外公示多语言词条册 Controller
 *
 * @author fuce
 * @date 2026-09-12
 */
@Api(value = "名录对外公示多语言词条册")
@Controller
@RequestMapping("/GiprTermBookController")
public class GiprTermBookController extends BaseController {

    private final String prefix = "admin/giprTermBook";

    @Autowired
    private ITGiprTermBookService giprTermBookService;

    @ApiOperation(value = "分页跳转", notes = "分页跳转")
    @GetMapping("/view")
    @RequiresPermissions("gipr:giprTermBook:view")
    public String view(ModelMap model) {
        return prefix + "/list";
    }

    @Log(title = "名录对外公示多语言词条册集合查询", action = "list")
    @ApiOperation(value = "分页查询", notes = "分页查询")
    @GetMapping("/list")
    @RequiresPermissions("gipr:giprTermBook:list")
    @ResponseBody
    public ResultTable list(TGiprTermBook record) {
        QueryWrapper<TGiprTermBook> queryWrapper = new QueryWrapper<TGiprTermBook>();
        startPage();
        com.github.pagehelper.PageInfo<TGiprTermBook> page =
                new com.github.pagehelper.PageInfo<TGiprTermBook>(giprTermBookService.selectTGiprTermBookList(queryWrapper));
        return pageTable(page.getList(), page.getTotal());
    }

    @Log(title = "名录对外公示多语言词条册新增", action = "add")
    @ApiOperation(value = "新增", notes = "新增")
    @PostMapping("/add")
    @RequiresPermissions("gipr:giprTermBook:add")
    @ResponseBody
    public AjaxResult add(TGiprTermBook record) {
        return toAjax(giprTermBookService.insertTGiprTermBook(record));
    }

    @Log(title = "名录对外公示多语言词条册修改", action = "edit")
    @ApiOperation(value = "修改保存", notes = "修改保存")
    @PostMapping("/edit")
    @RequiresPermissions("gipr:giprTermBook:edit")
    @ResponseBody
    public AjaxResult editSave(TGiprTermBook record) {
        return toAjax(giprTermBookService.updateTGiprTermBook(record));
    }

    @Log(title = "名录对外公示多语言词条册删除", action = "remove")
    @ApiOperation(value = "删除", notes = "删除")
    @DeleteMapping("/remove")
    @RequiresPermissions("gipr:giprTermBook:remove")
    @ResponseBody
    public AjaxResult remove(String ids) {
        return toAjax(giprTermBookService.deleteTGiprTermBookByIds(ids));
    }
}
