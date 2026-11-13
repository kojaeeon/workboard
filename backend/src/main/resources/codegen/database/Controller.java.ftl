package ${package};

import java.util.List;
import org.springframework.web.bind.annotation.*;
<#list imports as type>
import ${type};
</#list>

@RestController
@RequestMapping("/api/${endpoint}")
public class ${service}Controller {
    private final ${service}Service service;

    public ${service}Controller(${service}Service service) {
        this.service = service;
    }

    @GetMapping
    public List<${service}VO> findAll() { return service.findAll(); }
<#if keys?size gt 0>

    @GetMapping("${keyPath}")
    public ${service}VO findById(<#list keys as key>@PathVariable("${key.property}") ${key.javaType} ${key.property}<#sep>, </#list>) {
        return service.findById(${keyNames});
    }

    @DeleteMapping("${keyPath}")
    public int delete(<#list keys as key>@PathVariable("${key.property}") ${key.javaType} ${key.property}<#sep>, </#list>) {
        return service.delete(${keyNames});
    }
</#if>
<#if insertable?size gt 0>

    @PostMapping
    public int insert(@RequestBody ${service}VO value) { return service.insert(value); }
</#if>
<#if keys?size gt 0 && updatable?size gt 0>

    @PutMapping("${keyPath}")
    public int update(<#list keys as key>@PathVariable("${key.property}") ${key.javaType} ${key.property}<#sep>, </#list>, @RequestBody ${service}VO value) {
<#list keys as key>
        value.set${key.suffix}(${key.property});
</#list>
        return service.update(value);
    }
</#if>
}
