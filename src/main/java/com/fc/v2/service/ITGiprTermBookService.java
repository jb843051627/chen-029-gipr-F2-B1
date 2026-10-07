package com.fc.v2.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fc.v2.model.auto.TGiprTermBook;

import java.util.List;

/**
 * 名录对外公示多语言词条册 Service接口
 *
 * @author fuce
 * @date 2026-09-12
 */
public interface ITGiprTermBookService {

    /** 按主键查询 */
    TGiprTermBook selectTGiprTermBookById(Long id);

    /** 按条件查询列表（分页由调用方统一处理） */
    List<TGiprTermBook> selectTGiprTermBookList(Wrapper<TGiprTermBook> queryWrapper);

    /** 新增 */
    int insertTGiprTermBook(TGiprTermBook record);

    /** 修改 */
    int updateTGiprTermBook(TGiprTermBook record);

    /** 批量删除 */
    int deleteTGiprTermBookByIds(String ids);

    /** 按主键删除 */
    int deleteTGiprTermBookById(Long id);
}
