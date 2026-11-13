package ${package};

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
<#list imports as type>
import ${type};
</#list>

@Mapper
public interface ${service}Mapper {
    List<${service}VO> findAll();
<#if keys?size gt 0>
    ${service}VO findById(<#list keys as key>@Param("${key.property}") ${key.javaType} ${key.property}<#sep>, </#list>);
    int delete(<#list keys as key>@Param("${key.property}") ${key.javaType} ${key.property}<#sep>, </#list>);
</#if>
<#if insertable?size gt 0>
    int insert(${service}VO value);
</#if>
<#if keys?size gt 0 && updatable?size gt 0>
    int update(${service}VO value);
</#if>
}
