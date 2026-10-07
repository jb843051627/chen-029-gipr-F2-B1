package com.fc.v2.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.common.base.BaseController;
import com.fc.v2.common.domain.AjaxResult;
import com.fc.v2.common.domain.ResultTable;
import com.fc.v2.common.log.Log;
import com.fc.v2.model.auto.TGiprTermBook;
import com.fc.v2.service.ITGiprTermBookService;
import com.fc.v2.util.StringUtils;
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
 * 名录页与审核页共用同一组查询口径：状态、产品、取用式等条件全部显式透传，
 * service 不再暗藏 status=0，撤下条目在哪页能看由该页显式给状态决定，两页不打架。
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
        queryWrapper.eq(record.getStatus() != null, "status", record.getStatus());
        queryWrapper.eq(record.getProdId() != null, "prod_id", record.getProdId());
        queryWrapper.eq(record.getTermKind() != null, "term_kind", record.getTermKind());
        queryWrapper.eq(StringUtils.isNotEmpty(record.getLevelKind()), "level_kind", record.getLevelKind());
        queryWrapper.eq(StringUtils.isNotEmpty(record.getTermNo()), "term_no", record.getTermNo());
        queryWrapper.like(StringUtils.isNotEmpty(record.getTermName()), "term_name", record.getTermName());
        // 排序（ver_no、id 降序）与 del_flag 兜底由 service 统一追加，保证列表与详情同一口径
        startPage();
        com.github.pagehelper.PageInfo<TGiprTermBook> page =
                new com.github.pagehelper.PageInfo<TGiprTermBook>(giprTermBookService.selectTGiprTermBookList(queryWrapper));
        return pageTable(page.getList(), page.getTotal());
    }

    @Log(title = "名录对外公示多语言词条册详情", action = "view")
    @ApiOperation(value = "详情", notes = "按主键查同一条（与列表同一过滤口径）")
    @GetMapping("/detail")
    @RequiresPermissions("gipr:giprTermBook:view")
    @ResponseBody
    public AjaxResult detail(Long id) {
        TGiprTermBook term = giprTermBookService.selectTGiprTermBookById(id);
        return term == null ? AjaxResult.error("词条不存在") : AjaxResult.success().put("data", term);
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
    @ApiOperation(value = "修改保存", notes = "修改保存（须带 ver_no，并发时只认头一遭写入）")
    @PostMapping("/edit")
    @RequiresPermissions("gipr:giprTermBook:edit")
    @ResponseBody
    public AjaxResult editSave(TGiprTermBook record) {
        int rows = giprTermBookService.updateTGiprTermBook(record);
        if (rows > 0) {
            return AjaxResult.success();
        }
        return AjaxResult.error("保存失败：词条已被他人先改过，请刷新后取最新版次再保存");
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
