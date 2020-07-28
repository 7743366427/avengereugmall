package com.avengereug.mall.product.service.impl;

import com.avengereug.mall.common.anno.GlobalTransactional;
import com.avengereug.mall.product.entity.AttrAttrgroupRelationEntity;
import com.avengereug.mall.product.entity.AttrGroupEntity;
import com.avengereug.mall.product.entity.CategoryEntity;
import com.avengereug.mall.product.service.AttrAttrgroupRelationService;
import com.avengereug.mall.product.service.AttrGroupService;
import com.avengereug.mall.product.service.CategoryService;
import com.avengereug.mall.product.vo.AttrRespVo;
import com.avengereug.mall.product.vo.AttrVo;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.avengereug.mall.common.utils.PageUtils;
import com.avengereug.mall.common.utils.Query;

import com.avengereug.mall.product.dao.AttrDao;
import com.avengereug.mall.product.entity.AttrEntity;
import com.avengereug.mall.product.service.AttrService;


@Service("attrService")
public class AttrServiceImpl extends ServiceImpl<AttrDao, AttrEntity> implements AttrService {


    @Autowired
    private AttrAttrgroupRelationService attrAttrgroupRelationService;

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private AttrGroupService attrGroupService;

    @Override
    public PageUtils queryPage(Map<String, Object> params) {
        IPage<AttrEntity> page = this.page(
                new Query<AttrEntity>().getPage(params),
                new QueryWrapper<AttrEntity>()
        );

        return new PageUtils(page);
    }

    @GlobalTransactional
    @Override
    public void saveDetail(AttrVo attr) {
        // 1. 插入attr
        AttrEntity attrEntity = new AttrEntity();
        // TODO spring 提供的对象copy方法，将源对象对应的属性copy至目标对象中  --> 待总结BeanUtils的用法
        BeanUtils.copyProperties(attr, attrEntity);
        this.save(attrEntity);

        // 2. 插入属性分组与属性关联关系表中的属性分组信息
        AttrAttrgroupRelationEntity entity = new AttrAttrgroupRelationEntity();
        entity.setAttrGroupId(attr.getAttrGroupId());
        // 此id在mybatis插入后，会自动填充至对象中。
        entity.setAttrId(attrEntity.getAttrId());
        attrAttrgroupRelationService.save(entity);
    }

    @Override
    public PageUtils queryDetail(Map<String, Object> params, Long catelogId) {
        QueryWrapper<AttrEntity> wrapper = new QueryWrapper<>();
        String key = (String) params.get("key");

        if (catelogId != 0) {
            wrapper.eq("attr_id", key);
        }

        if (StringUtils.isNotEmpty(key)) {
            // 根据key和attrName进行筛选
            wrapper.or().like("attr_name", key);
        }

        IPage<AttrEntity> page = this.page(
                new Query<AttrEntity>().getPage(params),
                wrapper
        );

        PageUtils pageUtils = new PageUtils(page);


        List<AttrRespVo> attrRespVos = page.getRecords().stream().map(attrEntity -> {
            AttrRespVo attrRespVo = new AttrRespVo();
            BeanUtils.copyProperties(attrEntity, attrRespVo);

            if (ObjectUtils.isNotEmpty(attrEntity.getCatelogId())) {
                CategoryEntity categoryEntity = categoryService.getById(attrEntity.getCatelogId());
                attrRespVo.setCatelogName(categoryEntity.getName());
            }

            // 再根据groupId和catelogId来查找它的名称
            // TODO 疑问：会不会出现一个attr同时出现在多个attrGroup中，如果是，这里将会出现问题
            AttrAttrgroupRelationEntity attrAttrgroupRelationEntity = attrAttrgroupRelationService.getOne(
                    new QueryWrapper<AttrAttrgroupRelationEntity>().eq("attr_id", attrEntity.getAttrId())
            );
            if (attrAttrgroupRelationEntity.getAttrGroupId() != null) {
                AttrGroupEntity attrGroupEntity = attrGroupService.getById(attrAttrgroupRelationEntity.getAttrGroupId());
                attrRespVo.setGroupName(attrGroupEntity.getAttrGroupName());
            }

            return attrRespVo;
        }).collect(Collectors.toList());

        pageUtils.setList(attrRespVos);

        return pageUtils;
    }

    @Override
    public AttrRespVo getAttrRespVoById(Long attrId) {
        AttrEntity attrEntity = this.getById(attrId);

        AttrRespVo attrRespVo = new AttrRespVo();
        BeanUtils.copyProperties(attrEntity, attrRespVo);


        // 填充catelogPath
        if (attrEntity.getCatelogId() != null) {
            List<Long> catelogPath = categoryService.findCatelogPath(attrEntity.getCatelogId());
            attrRespVo.setCatelogPath(catelogPath);
        }

        // 填充当前属性所属分组
        // TODO 疑问：会不会出现一个attr同时出现在多个attrGroup中，如果是，这里将会出现问题
        AttrAttrgroupRelationEntity relationEntity = attrAttrgroupRelationService.getOne(
                new QueryWrapper<AttrAttrgroupRelationEntity>().eq("attr_id", attrEntity.getAttrId())
        );

        attrRespVo.setAttrGroupId(relationEntity.getAttrGroupId());

        return attrRespVo;
    }

    @GlobalTransactional
    @Override
    public void updateDetail(AttrVo attrVo) {
        AttrEntity attrEntity = new AttrEntity();
        BeanUtils.copyProperties(attrVo, attrEntity);
        // 1. 更新自己，
        this.updateById(attrEntity);

        // 2. 更新所属的分组
        // 2.1）、当前属性一开始创建时，未指定分组，此时更新添加了分组，这时就是要插入
        // 2.2）、当前属性一开始创建时，指定了分组，此时更新修改了分组，这时就是更新
        AttrAttrgroupRelationEntity relationEntity = new AttrAttrgroupRelationEntity();
        relationEntity.setAttrGroupId(attrVo.getAttrGroupId());
        relationEntity.setAttrId(attrVo.getAttrId());
        int count = attrAttrgroupRelationService.count(
                new QueryWrapper<AttrAttrgroupRelationEntity>().eq("attr_id", attrEntity.getAttrId())
        );
        if (count > 0) {
            attrAttrgroupRelationService.update(relationEntity,
                    new QueryWrapper<AttrAttrgroupRelationEntity>().eq("attr_id", attrEntity.getAttrId())
            );
        } else {
            attrAttrgroupRelationService.save(relationEntity);
        }
    }

}