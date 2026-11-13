<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "https://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="${package}.${service}Mapper">
    <resultMap id="resultMap" type="${package}.${service}VO">
<#list columns as column>
        <#if column.primary><id<#else><result</#if> column="${column.name}" property="${column.property}" jdbcType="${column.jdbcType}"/>
</#list>
    </resultMap>

    <select id="findAll" resultMap="resultMap">
        SELECT ${selectColumns} FROM ${table}
    </select>
<#if keys?size gt 0>

    <select id="findById" resultMap="resultMap">
        SELECT ${selectColumns} FROM ${table}
        WHERE <#list keys as key>${key.quoted} = #{${key.property},jdbcType=${key.jdbcType}}<#sep> AND </#list>
    </select>

    <delete id="delete">
        DELETE FROM ${table}
        WHERE <#list keys as key>${key.quoted} = #{${key.property},jdbcType=${key.jdbcType}}<#sep> AND </#list>
    </delete>
</#if>
<#if insertable?size gt 0>

    <insert id="insert" parameterType="${package}.${service}VO">
        INSERT INTO ${table} (<#list insertable as column>${column.quoted}<#sep>, </#list>)
        VALUES (<#list insertable as column>#{${column.property},jdbcType=${column.jdbcType}}<#sep>, </#list>)
    </insert>
</#if>
<#if keys?size gt 0 && updatable?size gt 0>

    <update id="update" parameterType="${package}.${service}VO">
        UPDATE ${table}
        SET <#list updatable as column>${column.quoted} = #{${column.property},jdbcType=${column.jdbcType}}<#sep>, </#list>
        WHERE <#list keys as key>${key.quoted} = #{${key.property},jdbcType=${key.jdbcType}}<#sep> AND </#list>
    </update>
</#if>
</mapper>
