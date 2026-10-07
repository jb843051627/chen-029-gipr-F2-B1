package com.fc.v2.model.auto;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.io.Serializable;
import java.util.Date;

/**
 * 名录对外公示多语言词条册对象 t_gipr_term_book
 *
 * @author fuce
 * @date 2026-09-12
 */
@TableName("t_gipr_term_book")
@ApiModel(value = "TGiprTermBook", description = "名录对外公示多语言词条册")
public class TGiprTermBook implements Serializable {
    private static final long serialVersionUID = 1L;
    /** 核验串（随版次走） */
    @TableField("content")
    @ApiModelProperty(value = "核验串（随版次走）")
    private String content;


    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 名录词条代号（由系统排上，不留手填的格子） */
    @TableField("term_no")
    @ApiModelProperty(value = "名录词条代号（由系统排上，不留手填的格子）")
    private String termNo;

    /** 词条名目（汉文正名／外文对照／拼音缩写） */
    @TableField("term_name")
    @ApiModelProperty(value = "词条名目（汉文正名／外文对照／拼音缩写）")
    private String termName;

    /** 本行语种（汉／外／拼 三式各一行） */
    @TableField("term_lang")
    @ApiModelProperty(value = "本行语种（汉／外／拼 三式各一行）")
    private String termLang;

    /** 词条文面 */
    @TableField("term_text")
    @ApiModelProperty(value = "词条文面")
    private String termText;

    /** 词条取用三式 0只出汉文 1只出对照 2按层回落 */
    @TableField("term_kind")
    @ApiModelProperty(value = "词条取用三式 0只出汉文 1只出对照 2按层回落")
    private Integer termKind;

    /** 一屏并列展示的语种条数 1..3（量程外的数视同没填） */
    @TableField("lang_num")
    @ApiModelProperty(value = "一屏并列展示的语种条数 1..3（量程外的数视同没填）")
    private Integer langNum;

    /** 定档层级（省定／市定／县定／系统兜底） */
    @TableField("level_kind")
    @ApiModelProperty(value = "定档层级（省定／市定／县定／系统兜底）")
    private String levelKind;

    /** 继承自哪一条 */
    @TableField("parent_no")
    @ApiModelProperty(value = "继承自哪一条")
    private String parentNo;

    /** 挂在哪一个名录产品名下 */
    @TableField("prod_id")
    @ApiModelProperty(value = "挂在哪一个名录产品名下")
    private Integer prodId;

    /** 适用的名录产品代号（冗余自名录底册） */
    @TableField("prod_no")
    @ApiModelProperty(value = "适用的名录产品代号（冗余自名录底册）")
    private String prodNo;

    /** 名录全称（冗余一份，以名录底册为准回写） */
    @TableField("prod_name")
    @ApiModelProperty(value = "名录全称（冗余一份，以名录底册为准回写）")
    private String prodName;

    /** 改动次序（数值大的先说话） */
    @TableField("ver_no")
    @ApiModelProperty(value = "改动次序（数值大的先说话）")
    private Integer verNo;

    /** 本项应收录条数 */
    @TableField("crt_num")
    @ApiModelProperty(value = "本项应收录条数")
    private Integer crtNum;

    /** 已对齐条数 */
    @TableField("hit_num")
    @ApiModelProperty(value = "已对齐条数")
    private Integer hitNum;

    /** 还差几条（轧出来，不留填数的空位） */
    @TableField("lack_num")
    @ApiModelProperty(value = "还差几条（轧出来，不留填数的空位）")
    private Integer lackNum;

    /** 启用那一一日 */
    @TableField("eff_start")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "启用那一一日")
    private Date effStart;

    /** 交班那一一日(不含) */
    @TableField("eff_end")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "交班那一一日(不含)")
    private Date effEnd;

    /** 行的情形 0在位 1已撤下 */
    @TableField("status")
    @ApiModelProperty(value = "行的情形 0在位 1已撤下")
    private Integer status;

    /** 删除标记 0正常 1删除 */
    @TableField("del_flag")
    @ApiModelProperty(value = "删除标记 0正常 1删除")
    private Integer delFlag;

    /** 创建者 */
    @TableField(value = "create_by", fill = FieldFill.INSERT)
    @ApiModelProperty(value = "创建者")
    private String createBy;

    /** 创建时间 */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "创建时间")
    private Date createTime;

    /** 更新者 */
    @TableField(value = "update_by", fill = FieldFill.UPDATE)
    @ApiModelProperty(value = "更新者")
    private String updateBy;

    /** 更新时间 */
    @TableField(value = "update_time", fill = FieldFill.UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "更新时间")
    private Date updateTime;

    /** 备注 */
    @TableField("remark")
    @ApiModelProperty(value = "备注")
    private String remark;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTermNo() {
        return termNo;
    }

    public void setTermNo(String termNo) {
        this.termNo = termNo;
    }

    public String getTermName() {
        return termName;
    }

    public void setTermName(String termName) {
        this.termName = termName;
    }

    public String getTermLang() {
        return termLang;
    }

    public void setTermLang(String termLang) {
        this.termLang = termLang;
    }

    public String getTermText() {
        return termText;
    }

    public void setTermText(String termText) {
        this.termText = termText;
    }

    public Integer getTermKind() {
        return termKind;
    }

    public void setTermKind(Integer termKind) {
        this.termKind = termKind;
    }

    public Integer getLangNum() {
        return langNum;
    }

    public void setLangNum(Integer langNum) {
        this.langNum = langNum;
    }

    public String getLevelKind() {
        return levelKind;
    }

    public void setLevelKind(String levelKind) {
        this.levelKind = levelKind;
    }

    public String getParentNo() {
        return parentNo;
    }

    public void setParentNo(String parentNo) {
        this.parentNo = parentNo;
    }

    public Integer getProdId() {
        return prodId;
    }

    public void setProdId(Integer prodId) {
        this.prodId = prodId;
    }

    public String getProdNo() {
        return prodNo;
    }

    public void setProdNo(String prodNo) {
        this.prodNo = prodNo;
    }

    public String getProdName() {
        return prodName;
    }

    public void setProdName(String prodName) {
        this.prodName = prodName;
    }

    public Integer getVerNo() {
        return verNo;
    }

    public void setVerNo(Integer verNo) {
        this.verNo = verNo;
    }

    public Integer getCrtNum() {
        return crtNum;
    }

    public void setCrtNum(Integer crtNum) {
        this.crtNum = crtNum;
    }

    public Integer getHitNum() {
        return hitNum;
    }

    public void setHitNum(Integer hitNum) {
        this.hitNum = hitNum;
    }

    public Integer getLackNum() {
        return lackNum;
    }

    public void setLackNum(Integer lackNum) {
        this.lackNum = lackNum;
    }

    public Date getEffStart() {
        return effStart;
    }

    public void setEffStart(Date effStart) {
        this.effStart = effStart;
    }

    public Date getEffEnd() {
        return effEnd;
    }

    public void setEffEnd(Date effEnd) {
        this.effEnd = effEnd;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }
    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }


    public Integer getDelFlag() {
        return delFlag;
    }

    public void setDelFlag(Integer delFlag) {
        this.delFlag = delFlag;
    }

    public String getCreateBy() {
        return createBy;
    }

    public void setCreateBy(String createBy) {
        this.createBy = createBy;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public String getUpdateBy() {
        return updateBy;
    }

    public void setUpdateBy(String updateBy) {
        this.updateBy = updateBy;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
