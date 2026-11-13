package ${package};

import java.util.List;
import org.springframework.stereotype.Service;
<#list imports as type>
import ${type};
</#list>

@Service
public class ${service}Service {
    private final ${service}Mapper mapper;

    public ${service}Service(${service}Mapper mapper) {
        this.mapper = mapper;
    }

    public List<${service}VO> findAll() { return mapper.findAll(); }
<#if keys?size gt 0>
    public ${service}VO findById(${keyArgs}) { return mapper.findById(${keyNames}); }
    public int delete(${keyArgs}) { return mapper.delete(${keyNames}); }
</#if>
<#if insertable?size gt 0>
    public int insert(${service}VO value) { return mapper.insert(value); }
</#if>
<#if keys?size gt 0 && updatable?size gt 0>
    public int update(${service}VO value) { return mapper.update(value); }
</#if>
}
