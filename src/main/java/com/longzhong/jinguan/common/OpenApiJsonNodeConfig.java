package com.longzhong.jinguan.common;

import com.fasterxml.jackson.databind.JavaType;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverter;
import io.swagger.v3.core.converter.ModelConverterContext;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import java.lang.reflect.Type;
import java.util.Iterator;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * 把 Jackson JsonNode 固定导出为 {type: object}。
 *
 * <p>springdoc 反射 JsonNode 方法得到的属性顺序不稳定,会让 openapi 漂移门禁随机变红; 控制面 JsonNode 载荷本就是任意 JSON,按 object
 * 声明即可。
 */
@Component
public class OpenApiJsonNodeConfig implements ModelConverter {

  @Override
  public Schema<?> resolve(
      AnnotatedType type, ModelConverterContext context, Iterator<ModelConverter> chain) {
    Class<?> raw = erased(type.getType());
    if (raw != null && JsonNode.class.isAssignableFrom(raw)) {
      return new ObjectSchema();
    }
    return chain.hasNext() ? chain.next().resolve(type, context, chain) : null;
  }

  private static Class<?> erased(Type type) {
    if (type instanceof Class<?> clazz) {
      return clazz;
    }
    if (type instanceof JavaType javaType) {
      return javaType.getRawClass();
    }
    return null;
  }
}
