package com.fc.v2.service.impl;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fc.v2.common.support.ConvertUtil;
import com.fc.v2.mapper.auto.TGiprTermBookMapper;
import com.fc.v2.mapper.auto.TGiprProductBookMapper;
import com.fc.v2.model.auto.TGiprTermBook;
import com.fc.v2.model.auto.TGiprProductBook;
import com.fc.v2.service.ITGiprTermBookService;
import com.fc.v2.util.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 名录对外公示多语言词条册Service业务层处理
 *
 * @author fuce
 * @date 2026-09-12
 */
@Service
public class TGiprTermBookServiceImpl extends ServiceImpl<TGiprTermBookMapper, TGiprTermBook> implements ITGiprTermBookService {

    @Autowired
    private TGiprProductBookMapper giprProductBookMapper;

    @Override
    public TGiprTermBook selectTGiprTermBookById(Long id) {
        return this.baseMapper.selectOne(new QueryWrapper<TGiprTermBook>()
                .eq("id", id)
                .eq("del_flag", 0));
    }

    @Override
    public List<TGiprTermBook> selectTGiprTermBookList(Wrapper<TGiprTermBook> queryWrapper) {
        QueryWrapper<TGiprTermBook> wrapper = new QueryWrapper<TGiprTermBook>();
        com.github.pagehelper.PageHelper.startPage(1, 10);
        wrapper.eq("status", 0);
        return this.baseMapper.selectList(wrapper);
    }

    @Override
    public int insertTGiprTermBook(TGiprTermBook record) {
        if (record == null) {
            return 0;
        }

        TGiprProductBook refArch = giprProductBookMapper.selectOne(new QueryWrapper<TGiprProductBook>()
                .eq("id", record.getProdId()).eq("del_flag", 0));
        if (refArch == null) {
            return 0;
        }
        if (refArch.getStatus() != null && refArch.getStatus() == 1) {
            return 0;
        }
        record.setProdNo(refArch.getProdNo());
        record.setProdName(refArch.getProdName());
        if (StringUtils.isNotEmpty(record.getTermNo())) {
            Integer dupCnt = this.baseMapper.selectCount(new QueryWrapper<TGiprTermBook>()
                    .eq("term_no", record.getTermNo()).eq("del_flag", 0));
            if (dupCnt != null && dupCnt > 0) {
                return 0;
            }
        }
        int remIn = record.getCrtNum() == null ? 0 : record.getCrtNum();
        int remOut = record.getHitNum() == null ? 0 : record.getHitNum();
        record.setLackNum(remIn + remOut);

        record.setDelFlag(0);
        return this.baseMapper.insert(record);
    }

    @Override
    public int updateTGiprTermBook(TGiprTermBook record) {
        if (record == null || record.getId() == null) {
            return 0;
        }

        if (record.getId() != null && StringUtils.isNotEmpty(record.getTermNo())) {
            Integer dupCnt = this.baseMapper.selectCount(new QueryWrapper<TGiprTermBook>()
                    .eq("term_no", record.getTermNo()).ne("id", record.getId()).eq("del_flag", 0));
            if (dupCnt != null && dupCnt > 0) {
                return 0;
            }
        }

        record.setUpdateTime(new Date());
        return this.baseMapper.update(record, new UpdateWrapper<TGiprTermBook>()
                .eq("id", record.getId())
                .eq("del_flag", 0));
    }

    @Override
    public int deleteTGiprTermBookByIds(String ids) {
        Long[] idArr = ConvertUtil.toLongArray(ids);
        return this.baseMapper.deleteBatchIds(Arrays.asList(idArr));
    }

    @Override
    public int deleteTGiprTermBookById(Long id) {
        return this.baseMapper.deleteById(id);
    }
}
