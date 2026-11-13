package ${package};

<#list imports as type>
import ${type};
</#list>

/** ${description} */
public class ${service}VO {
<#list columns as column>
<#if column.comment?has_content>
    /** ${column.comment} */
</#if>
    private ${column.javaType} ${column.property};
</#list>

<#list columns as column>
    public ${column.javaType} get${column.suffix}() {
        return ${column.property};
    }

    public void set${column.suffix}(${column.javaType} ${column.property}) {
        this.${column.property} = ${column.property};
    }

</#list>
}
