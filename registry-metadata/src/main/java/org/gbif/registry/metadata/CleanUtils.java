package org.gbif.registry.metadata;

import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.util.Collection;
import java.util.Iterator;

import org.apache.commons.beanutils.PropertyUtils;
import org.apache.commons.lang3.StringUtils;

public class CleanUtils {

  /**
   * Takes a bean and goes through all String properties and Collection properties with a generic String type
   * and replaces all blank values, i.e. empty strings or pure whitespace values, with nulls.
   * In case of collections those values will be removed
   * @param obj bean to clean
   */
  public static <T> void removeEmptyStrings(T obj) {
    for (PropertyDescriptor pd : PropertyUtils.getPropertyDescriptors(obj)) {
      try {
        if (String.class.equals(pd.getPropertyType()) && StringUtils
          .isBlank((String) PropertyUtils.getProperty(obj, pd.getName()))) {
          PropertyUtils.setProperty(obj, pd.getName(), null);
        } else if (Collection.class.isAssignableFrom(pd.getPropertyType())) {
          // inspect also list string values
          Field collField = obj.getClass().getDeclaredField(pd.getName());
          ParameterizedType collType = (ParameterizedType) collField.getGenericType();
          Class<?> collClass = (Class<?>) collType.getActualTypeArguments()[0];
          if (collClass.equals(String.class)) {
            Collection<String> list = (Collection<String>) PropertyUtils.getProperty(obj, pd.getName());
            if (list != null) {
              Iterator<String> iter = list.iterator();
              while (iter.hasNext()) {
                if (StringUtils.isBlank(iter.next())) {
                  iter.remove();
                }
              }
            }
          }
        }
      } catch (Exception e) {
        // ignore
      }
    }
  }

}
