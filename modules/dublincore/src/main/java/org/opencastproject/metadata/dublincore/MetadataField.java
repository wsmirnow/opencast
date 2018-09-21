/**
 * Licensed to The Apereo Foundation under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 *
 * The Apereo Foundation licenses this file to you under the Educational
 * Community License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License
 * at:
 *
 *   http://opensource.org/licenses/ecl2.txt
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations under
 * the License.
 *
 */

package org.opencastproject.metadata.dublincore;

import static com.entwinemedia.fn.data.json.Jsons.arr;
import static com.entwinemedia.fn.data.json.Jsons.f;
import static com.entwinemedia.fn.data.json.Jsons.obj;
import static com.entwinemedia.fn.data.json.Jsons.v;
import static org.apache.commons.lang3.exception.ExceptionUtils.getMessage;
import static org.apache.commons.lang3.exception.ExceptionUtils.getStackTrace;

import com.entwinemedia.fn.Fn;
import com.entwinemedia.fn.data.Opt;
import com.entwinemedia.fn.data.json.Field;
import com.entwinemedia.fn.data.json.JObject;
import com.entwinemedia.fn.data.json.JValue;
import com.entwinemedia.fn.data.json.Jsons;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.json.simple.JSONArray;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TimeZone;

/**
 * This is a generic and very abstract view of a certain field/property in a metadata catalog. The main purpose of this
 * class is to have a generic access to the variety of information stored in metadata catalogs.
 *
 * @param <A>
 *          Defines the type of the metadata value
 */
public class MetadataField<A> {

  private static final Logger logger = LoggerFactory.getLogger(MetadataField.class);

  public static final String PATTERN_DURATION = "HH:mm:ss";

  /** Keys for the different values in the configuration file */
  public static final String CONFIG_COLLECTION_ID_KEY = "collectionID";
  public static final String CONFIG_PATTERN_KEY = "pattern";
  public static final String CONFIG_END_DATE_OUTPUT_KEY = "endDateOutputID";
  public static final String CONFIG_END_TIME_OUTPUT_KEY = "endTimeOutputID";
  public static final String CONFIG_INPUT_ID_KEY = "inputID";
  public static final String CONFIG_LABEL_KEY = "label";
  public static final String CONFIG_LIST_PROVIDER_KEY = "listprovider";
  public static final String CONFIG_NAMESPACE_KEY = "namespace";
  public static final String CONFIG_ORDER_KEY = "order";
  public static final String CONFIG_OUTPUT_ID_KEY = "outputID";
  public static final String CONFIG_PROPERTY_PREFIX = "property";
  public static final String CONFIG_READ_ONLY_KEY = "readOnly";
  public static final String CONFIG_REQUIRED_KEY = "required";
  public static final String CONFIG_START_DATE_OUTPUT_KEY = "startDateOutputID";
  public static final String CONFIG_START_TIME_OUTPUT_KEY = "startTimeOutputID";
  public static final String CONFIG_TYPE_KEY = "type";

  /* Keys for the different properties of the metadata JSON Object */
  protected static final String JSON_KEY_ID = "id";
  protected static final String JSON_KEY_LABEL = "label";
  protected static final String JSON_KEY_READONLY = "readOnly";
  protected static final String JSON_KEY_REQUIRED = "required";
  protected static final String JSON_KEY_TYPE = "type";
  protected static final String JSON_KEY_VALUE = "value";
  protected static final String JSON_KEY_COLLECTION = "collection";
  protected static final String JSON_KEY_TRANSLATABLE = "translatable";

  /** Labels for the temporal date fields */
  private static final String LABEL_METADATA_PREFIX = "EVENTS.EVENTS.DETAILS.METADATA.";
  private static final String LABEL_METADATA_END_DATE = LABEL_METADATA_PREFIX + "END_DATE";
  private static final String LABEL_METADATA_END_TIME = LABEL_METADATA_PREFIX + "END_TIME";
  private static final String LABEL_METADATA_DURATION = LABEL_METADATA_PREFIX + "DURATION";
  private static final String LABEL_METADATA_START_DATE = LABEL_METADATA_PREFIX + "START_DATE";
  private static final String LABEL_METADATA_START_TIME = LABEL_METADATA_PREFIX + "START_TIME";

  /**
   * Possible types for the metadata field. The types are used in the frontend and backend to know how the metadata
   * fields should be formatted (if needed).
   */
  public enum Type {
    BOOLEAN, DATE, DURATION, ITERABLE_TEXT, MIXED_TEXT, ORDERED_TEXT, LONG, START_DATE, START_TIME, TEXT, TEXT_LONG
  }

  public enum JsonType {
    BOOLEAN, DATE, NUMBER, TEXT, MIXED_TEXT, ORDERED_TEXT, TEXT_LONG, TIME
  }

  /** The id of a collection to validate values against. */
  private Opt<String> collectionID = Opt.none();
  /** The format to use for temporal date properties. */
  private Opt<String> pattern = Opt.none();
  /** The id of the field used to identify it in the dublin core. */
  private String inputID;
  /** The i18n id for the label to show the property. */
  private String label;
  /** The provider to populate the property with. */
  private Opt<String> listprovider = Opt.none();
  /** The optional namespace of the field used if a field can be found in more than one namespace */
  private Opt<String> namespace = Opt.some(DublinCore.TERMS_NS_URI);
  /**
   * In the order of properties where this property should be oriented in the UI i.e. 0 means the property should come
   * first, 1 means it should come second etc.
   */
  private Opt<Integer> order = Opt.none();
  /** The optional id of the field used to output for the ui, if not present will assume the same as the inputID. */
  private Opt<String> outputID = Opt.none();
  /** Whether the property should not be edited. */
  private boolean readOnly;
  /** Whether the property is required to update the metadata. */
  private boolean required;
  /** The type of the metadata for example text, date etc. */
  private Type type;
  /** The type of the metadata for the json to use example text, date, time, number etc. */
  private JsonType jsonType;

  private Opt<A> value = Opt.none();
  private Opt<Boolean> translatable = Opt.none();
  private boolean updated = false;
  private Opt<Map<String, String>> collection = Opt.none();
  private Fn<Opt<A>, JValue> valueToJSON;
  private Fn<Object, A> jsonToValue;
  private Opt<String> durationOutputID = Opt.none();

  public MetadataField() {
  }

  /**
   * Metadata field constructor
   *
   * @param inputID
   *          The identifier of the new metadata field
   * @param label
   *          the label of the field. The string displayed next to the field value on the frontend. This is usually be a
   *          translation key
   * @param readOnly
   *          Define if the new metadata field can be or not edited
   * @param required
   *          Define if the new metadata field is or not required
   * @param value
   *          The metadata field value
   * @param type
   *          The metadata field type @ EventMetadata.Type}
   * @param collection
   *          If the field has a limited list of possible value, the option should contain this one. Otherwise it should
   *          be none. This is also possible to use the collectionId parameter for that.
   * @param collectionID
   *          The id of the limit list of possible value that should be get through the resource endpoint.
   * @param valueToJSON
   *          Function to format the metadata field value to a JSON value.
   * @param jsonToValue
   *          Function to parse the JSON value of the metadata field.
   * @throws IllegalArgumentException
   *           if the id, label, type, valueToJSON or/and jsonToValue parameters is/are null
   */
  private MetadataField(String inputID, Opt<String> outputID, String label, boolean readOnly, boolean required, A value,
          Opt<Boolean> translatable, Type type, JsonType jsonType, Opt<Map<String, String>> collection,
          Opt<String> collectionID, Fn<Opt<A>, JValue> valueToJSON, Fn<Object, A> jsonToValue, Opt<Integer> order,
          Opt<String> namespace)
                  throws IllegalArgumentException {
    if (valueToJSON == null)
      throw new IllegalArgumentException("The function 'valueToJSON' must not be null.");
    if (jsonToValue == null)
      throw new IllegalArgumentException("The function 'jsonToValue' must not be null.");
    if (StringUtils.isBlank(inputID))
      throw new IllegalArgumentException("The metadata input id must not be null.");
    if (StringUtils.isBlank(label))
      throw new IllegalArgumentException("The metadata label must not be null.");
    if (type == null)
      throw new IllegalArgumentException("The metadata type must not be null.");

    this.inputID = inputID;
    this.outputID = outputID;
    this.label = label;
    this.readOnly = readOnly;
    this.required = required;
    if (value == null)
      this.value = Opt.none();
    else
      this.value = Opt.some(value);
    this.translatable = translatable;
    this.type = type;
    this.jsonType = jsonType;
    this.collection = collection;
    this.collectionID = collectionID;
    this.valueToJSON = valueToJSON;
    this.jsonToValue = jsonToValue;
    this.order = order;
    this.namespace = namespace;
  }

  /**
   * Set the option of a limited list of possible values.
   *
   * @param collection
   *          The option of a limited list of possible values
   */
  public void setCollection(Opt<Map<String, String>> collection) {
    if (collection == null)
      this.collection = Opt.none();
    else {
      this.collection = collection;
    }
  }

  public JObject toJSON() {
    Map<String, Field> values = new HashMap<>();
    values.put(JSON_KEY_ID, f(JSON_KEY_ID, v(getOutputID(), Jsons.BLANK)));
    values.put(JSON_KEY_LABEL, f(JSON_KEY_LABEL, v(label, Jsons.BLANK)));
    values.put(JSON_KEY_VALUE, f(JSON_KEY_VALUE, valueToJSON.apply(value)));
    values.put(JSON_KEY_TYPE, f(JSON_KEY_TYPE, v(jsonType.toString().toLowerCase(), Jsons.BLANK)));
    values.put(JSON_KEY_READONLY, f(JSON_KEY_READONLY, v(readOnly)));
    values.put(JSON_KEY_REQUIRED, f(JSON_KEY_REQUIRED, v(required)));

    if (collection.isSome())
      values.put(JSON_KEY_COLLECTION, f(JSON_KEY_COLLECTION, mapToJSON(collection.get())));
    else if (collectionID.isSome())
      values.put(JSON_KEY_COLLECTION, f(JSON_KEY_COLLECTION, v(collectionID.get())));
    if (translatable.isSome())
      values.put(JSON_KEY_TRANSLATABLE, f(JSON_KEY_TRANSLATABLE, v(translatable.get())));
    return obj(values);
  }

  public void fromJSON(Object json) {
    this.setValue(jsonToValue.apply(json));
  }

  public Opt<Map<String, String>> getCollection() {
    return collection;
  }

  public Opt<A> getValue() {
    return value;
  }

  public Opt<Boolean> isTranslatable() {
    return translatable;
  }

  public boolean isUpdated() {
    return updated;
  }

  public void setValue(A value) {
    if (value == null)
      this.value = Opt.none();
    else {
      this.value = Opt.some(value);
      this.updated = true;
    }
  }

  public void setIsTranslatable(Opt<Boolean> translatable) {
    this.translatable = translatable;
  }

  public static SimpleDateFormat getSimpleDateFormatter(String pattern) {
    final SimpleDateFormat dateFormat;
    if (StringUtils.isNotBlank(pattern)) {
      dateFormat = new SimpleDateFormat(pattern);
    } else {
      dateFormat = new SimpleDateFormat();
    }
    dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    return dateFormat;
  }

  /**
   * Create a metadata field based on a {@link Boolean}.
   *
   * @param inputID
   *          The identifier of the new metadata field
   * @param label
   *          The label of the new metadata field
   * @param readOnly
   *          Define if the new metadata is or not a readonly field
   * @param required
   *          Define if the new metadata field is or not required
   * @param order
   *          The ui order for the new field, 0 at the top and progressively down from there.
   * @return The new metadata field
   *
   */
  public static MetadataField<Boolean> createBooleanMetadata(String inputID, Opt<String> outputID, String label,
          boolean readOnly, boolean required, Opt<Integer> order, Opt<String> namespace) {

    Fn<Opt<Boolean>, JValue> booleanToJson = new Fn<Opt<Boolean>, JValue>() {
      @Override
      public JValue apply(Opt<Boolean> value) {
        if (value.isNone())
          return Jsons.BLANK;
        else {
          return v(value.get(), Jsons.BLANK);
        }
      }
    };

    Fn<Object, Boolean> jsonToBoolean = new Fn<Object, Boolean>() {
      @Override
      public Boolean apply(Object value) {
        if (value instanceof Boolean) {
          return (Boolean) value;
        }
        String stringValue = value.toString();
        if (StringUtils.isBlank(stringValue)) {
          return null;
        }
        return Boolean.parseBoolean(stringValue);
      }
    };

    return new MetadataField<>(inputID, outputID, label, readOnly, required, null, Opt.none(), Type.BOOLEAN, JsonType.BOOLEAN,
            Opt.<Map<String, String>> none(), Opt.<String> none(), booleanToJson, jsonToBoolean, order, namespace);
  }

  /**
   * Creates a copy of a {@link MetadataField} and sets the value based upon a string.
   *
   * @param oldField
   *          The field whose other values such as ids, label etc. will be copied.
   * @param value
   *          The value that will be interpreted as being from a JSON value.
   * @return A new {@link MetadataField} with the value set
   */
  public static MetadataField<?> copyMetadataFieldWithValue(MetadataField<?> oldField, String value) {
    MetadataField<?> newField = null;
    switch (oldField.getType()) {
      case BOOLEAN:
        MetadataField<Boolean> booleanField = MetadataField.createBooleanMetadata(oldField.getInputID(),
                Opt.some(oldField.getOutputID()), oldField.getLabel(), oldField.isReadOnly(), oldField.isRequired(),
                oldField.getOrder(), oldField.getNamespace());
        booleanField.fromJSON(value);
        return booleanField;
      case DATE:
        MetadataField<Date> dateField = MetadataField.createDateMetadata(oldField.getInputID(),
                Opt.some(oldField.getOutputID()), oldField.getLabel(), oldField.isReadOnly(), oldField.isRequired(),
                oldField.getPattern().get(), oldField.getOrder(), oldField.getNamespace());
        dateField.fromJSON(value);
        return dateField;
      case DURATION:
        MetadataField<String> durationField = MetadataField.createDurationMetadataField(oldField.getInputID(),
                Opt.some(oldField.getOutputID()), oldField.getLabel(), oldField.isReadOnly(), oldField.isRequired(),
                oldField.getOrder(), oldField.getNamespace());
        durationField.fromJSON(value);
        return durationField;
      case ITERABLE_TEXT:
        MetadataField<Iterable<String>> iterableTextField = MetadataField.createIterableStringMetadataField(
                oldField.getInputID(), Opt.some(oldField.getOutputID()), oldField.getLabel(), oldField.isReadOnly(),
                oldField.isRequired(), oldField.isTranslatable(), oldField.getCollection(), oldField.getCollectionID(),
                oldField.getOrder(), oldField.getNamespace());
        iterableTextField.fromJSON(value);
        return iterableTextField;
      case LONG:
        MetadataField<Long> longField = MetadataField.createLongMetadataField(oldField.getInputID(),
                Opt.some(oldField.getOutputID()), oldField.getLabel(), oldField.isReadOnly(), oldField.isRequired(),
                oldField.isTranslatable(), oldField.getCollection(), oldField.getCollectionID(), oldField.getOrder(),
                oldField.getNamespace());
        longField.fromJSON(value);
        return longField;
      case MIXED_TEXT:
        MetadataField<Iterable<String>> mixedField = MetadataField.createMixedIterableStringMetadataField(
                oldField.getInputID(), Opt.some(oldField.getOutputID()), oldField.getLabel(), oldField.isReadOnly(),
                oldField.isRequired(), oldField.isTranslatable(), oldField.getCollection(), oldField.getCollectionID(),
                oldField.getOrder(), oldField.getNamespace());
        mixedField.fromJSON(value);
        return mixedField;
      case START_DATE:
        MetadataField<String> startDateField = MetadataField.createTemporalStartDateMetadata(oldField.getInputID(),
                Opt.some(oldField.getOutputID()), oldField.getLabel(), oldField.isReadOnly(), oldField.isRequired(),
                oldField.getPattern().get(), oldField.getOrder(), oldField.getNamespace());
        startDateField.fromJSON(value);
        return startDateField;
      case START_TIME:
        MetadataField<String> startTimeField = MetadataField.createTemporalStartTimeMetadata(oldField.getInputID(),
                Opt.some(oldField.getOutputID()), oldField.getLabel(), oldField.isReadOnly(), oldField.isRequired(),
                oldField.getPattern().get(), oldField.getOrder(), oldField.getNamespace());
        startTimeField.fromJSON(value);
        return startTimeField;
      case TEXT:
        MetadataField<String> textField = MetadataField.createTextMetadataField(oldField.getInputID(),
                Opt.some(oldField.getOutputID()), oldField.getLabel(), oldField.isReadOnly(), oldField.isRequired(),
                oldField.isTranslatable(), oldField.getCollection(), oldField.getCollectionID(), oldField.getOrder(),
                oldField.getNamespace());
        textField.fromJSON(value);
        return textField;
      case TEXT_LONG:
        MetadataField<String> textLongField = MetadataField.createTextLongMetadataField(oldField.getInputID(),
                Opt.some(oldField.getOutputID()), oldField.getLabel(), oldField.isReadOnly(), oldField.isRequired(),
                oldField.isTranslatable(), oldField.getCollection(), oldField.getCollectionID(), oldField.getOrder(),
                oldField.getNamespace());
        textLongField.fromJSON(value);
        return textLongField;
      case ORDERED_TEXT:
        MetadataField<String> orderedTextField = MetadataField.createOrderedTextMetadataField(oldField.getInputID(),
            Opt.some(oldField.getOutputID()), oldField.getLabel(), oldField.isReadOnly(), oldField.isRequired(),
            oldField.isTranslatable(), oldField.getCollection(), oldField.getCollectionID(), oldField.getOrder(),
            oldField.getNamespace());
        orderedTextField.fromJSON(value);
        return orderedTextField;
      default:
        return newField;
    }
  }

  /**
   * Create a metadata field based on a {@link Date}.
   *
   * @param inputID
   *          The identifier of the new metadata field
   * @param label
   *          The label of the new metadata field
   * @param readOnly
   *          Define if the new metadata is or not a readonly field
   * @param required
   *          Define if the new metadata field is or not required
   * @param pattern
   *          The date pattern for {@link SimpleDateFormat}.
   * @param order
   *          The ui order for the new field, 0 at the top and progressively down from there.
   * @return The new metadata field
   *
   */
  public static MetadataField<Date> createDateMetadata(String inputID, Opt<String> outputID, String label,
          boolean readOnly, boolean required, final String pattern, Opt<Integer> order, Opt<String> namespace) {
    final SimpleDateFormat dateFormat = getSimpleDateFormatter(pattern);

    Fn<Opt<Date>, JValue> dateToJSON = new Fn<Opt<Date>, JValue>() {
      @Override
      public JValue apply(Opt<Date> date) {
        if (date.isNone())
          return Jsons.BLANK;
        else {
          return v(dateFormat.format(date.get()), Jsons.BLANK);
        }
      }
    };

    Fn<Object, Date> jsonToDate = new Fn<Object, Date>() {
      @Override
      public Date apply(Object value) {
        try {
          String date = (String) value;

          if (StringUtils.isBlank(date))
            return null;

          return dateFormat.parse(date);
        } catch (java.text.ParseException e) {
          logger.error("Not able to parse date {}: {}", value, e.getMessage());
          return null;
        }
      }
    };

    MetadataField<Date> dateField = new MetadataField<>(inputID, outputID, label, readOnly, required, null, Opt.none(),
            Type.DATE, JsonType.DATE, Opt.<Map<String, String>> none(), Opt.<String> none(), dateToJSON, jsonToDate,
            order, namespace);
    if (StringUtils.isNotBlank(pattern)) {
      dateField.setPattern(Opt.some(pattern));
    }
    return dateField;
  }

  public static MetadataField<String> createDurationMetadataField(String inputID, Opt<String> outputID, String label,
          boolean readOnly, boolean required, Opt<Integer> order, Opt<String> namespace) {
    return createDurationMetadataField(inputID, outputID, label, readOnly, required, Opt.<Boolean> none(),
            Opt.<Map<String, String>> none(), Opt.<String> none(), order, namespace);
  }

  public static MetadataField<String> createDurationMetadataField(String inputID, Opt<String> outputID, String label,
          boolean readOnly, boolean required, Opt<Boolean> isTranslatable, Opt<Map<String, String>> collection,
          Opt<String> collectionId, Opt<Integer> order, Opt<String> namespace) {

    Fn<Opt<String>, JValue> periodToJSON = new Fn<Opt<String>, JValue>() {
      @Override
      public JValue apply(Opt<String> value) {
        Long returnValue = 0L;
        DCMIPeriod period = EncodingSchemeUtils.decodePeriod(value.get());
        if (period != null && period.hasStart() && period.hasEnd()) {
          returnValue = period.getEnd().getTime() - period.getStart().getTime();
        } else {
          try {
            returnValue = Long.parseLong(value.get());
          } catch (NumberFormatException e) {
            logger.debug("Unable to parse duration '{}' as either period or millisecond duration.", value.get());
          }
        }
        return v(DurationFormatUtils.formatDuration(returnValue, PATTERN_DURATION));
      }
    };

    Fn<Object, String> jsonToPeriod = new Fn<Object, String>() {
      @Override
      public String apply(Object value) {
        if (!(value instanceof String)) {
          logger.warn("The given value for duration can not be parsed.");
          return "";
        }

        String duration = (String) value;
        String[] durationParts = duration.split(":");
        if (durationParts.length < 3)
          return null;
        Integer hours = Integer.parseInt(durationParts[0]);
        Integer minutes = Integer.parseInt(durationParts[1]);
        Integer seconds = Integer.parseInt(durationParts[2]);

        Long returnValue = ((hours.longValue() * 60 + minutes.longValue()) * 60 + seconds.longValue()) * 1000;

        return returnValue.toString();
      }
    };
    return new MetadataField<>(inputID, outputID, label, readOnly, required, "", isTranslatable, Type.DURATION,
            JsonType.TEXT, collection, collectionId, periodToJSON, jsonToPeriod, order, namespace);
  }

  /**
   * Create a metadata field of type mixed iterable String
   *
   * @param inputID
   *          The identifier of the new metadata field
   * @param label
   *          The label of the new metadata field
   * @param readOnly
   *          Define if the new metadata field can be or not edited
   * @param required
   *          Define if the new metadata field is or not required
   * @param isTranslatable
   *          If the field value is not human readable and should be translated before
   * @param collection
   *          If the field has a limited list of possible value, the option should contain this one. Otherwise it should
   *          be none.
   * @param order
   *          The ui order for the new field, 0 at the top and progressively down from there.
   * @return the new metadata field
   */
  public static MetadataField<Iterable<String>> createMixedIterableStringMetadataField(String inputID,
          Opt<String> outputID, String label, boolean readOnly, boolean required, Opt<Boolean> isTranslatable,
          Opt<Map<String, String>> collection, Opt<String> collectionId, Opt<Integer> order, Opt<String> namespace) {

    Fn<Opt<Iterable<String>>, JValue> iterableToJSON = new Fn<Opt<Iterable<String>>, JValue>() {
      @Override
      public JValue apply(Opt<Iterable<String>> value) {
        if (value.isNone())
          return arr();

        Object val = value.get();
        List<JValue> list = new ArrayList<>();

        if (val instanceof String) {
          // The value is a string so we need to split it.
          String stringVal = (String) val;
          for (String entry : stringVal.split(",")) {
            if (StringUtils.isNotBlank(entry))
              list.add(v(entry, Jsons.BLANK));
          }
        } else {
          // The current value is just an iterable string.
          for (Object v : value.get()) {
            list.add(v(v, Jsons.BLANK));
          }
        }
        return arr(list);
      }
    };

    Fn<Object, Iterable<String>> jsonToIterable = new Fn<Object, Iterable<String>>() {
      @Override
      public Iterable<String> apply(Object arrayIn) {
        JSONParser parser = new JSONParser();
        JSONArray array;
        if (arrayIn instanceof String) {
          try {
            array = (JSONArray) parser.parse((String) arrayIn);
          } catch (ParseException e) {
            throw new IllegalArgumentException("Unable to parse Mixed Iterable value into a JSONArray:", e);
          }
        } else {
          array = (JSONArray) arrayIn;
        }

        if (array == null)
          return new ArrayList<>();
        String[] arrayOut = new String[array.size()];
        for (int i = 0; i < array.size(); i++) {
          arrayOut[i] = (String) array.get(i);
        }
        return Arrays.asList(arrayOut);
      }

    };

    return new MetadataField<>(inputID, outputID, label, readOnly, required, new ArrayList<String>(), isTranslatable,
            Type.MIXED_TEXT, JsonType.MIXED_TEXT, collection, collectionId, iterableToJSON, jsonToIterable, order,
            namespace);
  }

  /**
   * Create a metadata field of type iterable String
   *
   * @param inputID
   *          The identifier of the new metadata field
   * @param label
   *          The label of the new metadata field
   * @param readOnly
   *          Define if the new metadata field can be or not edited
   * @param required
   *          Define if the new metadata field is or not required
   * @param isTranslatable
   *          If the field value is not human readable and should be translated before
   * @param collection
   *          If the field has a limited list of possible value, the option should contain this one. Otherwise it should
   *          be none.
   * @param order
   *          The ui order for the new field, 0 at the top and progressively down from there.
   * @return the new metadata field
   */
  public static MetadataField<Iterable<String>> createIterableStringMetadataField(String inputID, Opt<String> outputID,
          String label, boolean readOnly, boolean required, Opt<Boolean> isTranslatable,
          Opt<Map<String, String>> collection, Opt<String> collectionId, Opt<Integer> order, Opt<String> namespace) {

    Fn<Opt<Iterable<String>>, JValue> iterableToJSON = new Fn<Opt<Iterable<String>>, JValue>() {
      @Override
      public JValue apply(Opt<Iterable<String>> value) {
        if (value.isNone())
          return arr();

        Object val = value.get();
        List<JValue> list = new ArrayList<>();

        if (val instanceof String) {
          // The value is a string so we need to split it.
          String stringVal = (String) val;
          for (String entry : stringVal.split(",")) {
            list.add(v(entry, Jsons.BLANK));
          }
        } else {
          // The current value is just an iterable string.
          for (Object v : value.get()) {
            list.add(v(v, Jsons.BLANK));
          }
        }
        return arr(list);
      }
    };

    Fn<Object, Iterable<String>> jsonToIterable = new Fn<Object, Iterable<String>>() {
      @Override
      public Iterable<String> apply(Object arrayIn) {
        JSONArray array = (JSONArray) arrayIn;
        if (array == null)
          return null;
        String[] arrayOut = new String[array.size()];
        for (int i = 0; i < array.size(); i++) {
          arrayOut[i] = (String) array.get(i);
        }
        return Arrays.asList(arrayOut);
      }

    };

    return new MetadataField<>(inputID, outputID, label, readOnly, required, new ArrayList<String>(), isTranslatable,
            Type.ITERABLE_TEXT, JsonType.TEXT, collection, collectionId, iterableToJSON, jsonToIterable, order,
            namespace);
  }

  public static MetadataField<Long> createLongMetadataField(String inputID, Opt<String> outputID, String label,
          boolean readOnly, boolean required, Opt<Boolean> isTranslatable, Opt<Map<String, String>> collection,
          Opt<String> collectionId, Opt<Integer> order, Opt<String> namespace) {

    Fn<Opt<Long>, JValue> longToJSON = new Fn<Opt<Long>, JValue>() {
      @Override
      public JValue apply(Opt<Long> value) {
        if (value.isNone())
          return Jsons.BLANK;
        else
          return v(value.get().toString());
      }
    };

    Fn<Object, Long> jsonToLong = new Fn<Object, Long>() {
      @Override
      public Long apply(Object value) {
        if (!(value instanceof String)) {
          logger.warn("The given value for Long can not be parsed.");
          return 0L;
        }
        String longString = (String) value;
        return Long.parseLong(longString);
      }
    };

    return new MetadataField<>(inputID, outputID, label, readOnly, required, 0L, isTranslatable, Type.TEXT, JsonType.NUMBER,
            collection, collectionId, longToJSON, jsonToLong, order, namespace);
  }

  protected void setDurationOutputID(Opt<String> durationOutputID) {
    this.durationOutputID = durationOutputID;
  }

  protected Opt<String> getDurationOutputID() {
    return durationOutputID;
  }

  private static MetadataField<String> createTemporalMetadata(String inputID, Opt<String> outputID, String label,
          boolean readOnly, boolean required, final String pattern, final Type type, final JsonType jsonType,
          Opt<Integer> order, Opt<String> namespace) {
    if (StringUtils.isBlank(pattern)) {
      throw new IllegalArgumentException(
              "For temporal metadata field " + inputID + " of type " + type + " there needs to be a pattern.");
    }

    final SimpleDateFormat dateFormat = getSimpleDateFormatter(pattern);

    Fn<Object, String> jsonToDateString = new Fn<Object, String>() {
      @Override
      public String apply(Object value) {
        String date = (String) value;

        if (StringUtils.isBlank(date))
          return "";

        try {
          dateFormat.parse(date);
        } catch (java.text.ParseException e) {
          logger.error("Not able to parse date string {}: {}", value, getMessage(e));
          return null;
        }

        return date;
      }
    };

    Fn<Opt<String>, JValue> dateToJSON = new Fn<Opt<String>, JValue>() {
      @Override
      public JValue apply(Opt<String> periodEncodedString) {
        if (periodEncodedString.isNone() || StringUtils.isBlank(periodEncodedString.get())) {
          return Jsons.BLANK;
        }

        // Try to parse the metadata as DCIM metadata.
        DCMIPeriod p = EncodingSchemeUtils.decodePeriod(periodEncodedString.get());
        if (p != null) {
          return v(dateFormat.format(p.getStart()), Jsons.BLANK);
        }

        // Not DCIM metadata so it might already be formatted (given from the front and is being returned there
        try {
          dateFormat.parse(periodEncodedString.get());
          return v(periodEncodedString.get(), Jsons.BLANK);
        } catch (Exception e) {
          logger.error(
                  "Unable to parse temporal metadata '{}' as either DCIM data or a formatted date using pattern {} because: {}",
                  periodEncodedString.get(), pattern, getStackTrace(e));
          throw new IllegalArgumentException(e);
        }
      }
    };

    MetadataField<String> temporalStart = new MetadataField<>(inputID, outputID, label, readOnly, required, null,
            Opt.none(), type, jsonType, Opt.<Map<String, String>> none(), Opt.<String> none(), dateToJSON,
            jsonToDateString, order, namespace);
    temporalStart.setPattern(Opt.some(pattern));

    return temporalStart;
  }

  public static MetadataField<String> createTemporalStartDateMetadata(String inputID, Opt<String> outputID,
          String label, boolean readOnly, boolean required, final String pattern, Opt<Integer> order,
          Opt<String> namespace) {
    return createTemporalMetadata(inputID, outputID, label, readOnly, required, pattern, Type.START_DATE,
            JsonType.DATE, order, namespace);
  }

  public static MetadataField<String> createTemporalStartTimeMetadata(String inputID, Opt<String> outputID,
          String label, boolean readOnly, boolean required, final String pattern, Opt<Integer> order,
          Opt<String> namespace) {
    return createTemporalMetadata(inputID, outputID, label, readOnly, required, pattern, Type.START_TIME,
            JsonType.TIME, order, namespace);
  }

  /**
   * Add a temporal format {@link Date} field to the metadata
   *
   * @param metadataField
   *          The form of the field
   * @param label
   * @param p
   *          The data to put into the field
   * @param outputID
   *          The id to use for the new field.
   * @param pattern
   *          The {@link SimpleDateFormat} to format the {@link Date} field.
   * @param isStart
   *          Whether this field is a start or end value of the DCMIPeriod
   * @param order
   *          The ui order for the new field, 0 at the top and progressively down from there.
   */
  public static Opt<MetadataField<Date>> createTemporalDateMetadataField(MetadataField<?> metadataField, String label,
          Opt<DCMIPeriod> p, Opt<String> outputID, Opt<String> pattern, boolean isStart, Opt<Integer> order) {
    if (outputID.isNone()) {
      logger.debug("Skipping temporal property with label {} because its output id was not defined.", label);
      return Opt.none();
    }

    if (pattern.isNone()) {
      logger.warn("Skipping temporal JSON property with id {} because the date or time pattern was not defined for it.",
              outputID.get());
      return Opt.none();
    }

    MetadataField<Date> dateField = MetadataField.createDateMetadata(metadataField.getInputID(), outputID, label,
            metadataField.isReadOnly(), metadataField.isRequired(), pattern.get(), metadataField.getOrder(),
            metadataField.getNamespace());
    if (p.isSome()) {
      Date date;
      if (isStart) {
        date = p.get().getStart();
      } else {
        date = p.get().getEnd();
      }
      dateField.setValue(date);
    }
    return Opt.some(dateField);

  }

  public static Opt<MetadataField<String>> createTemporalDurationMetadataField(MetadataField<?> metadataField,
          String label, Opt<DCMIPeriod> p, Opt<String> outputID, Opt<Integer> order) {
    if (outputID.isNone()) {
      logger.debug("Skipping temporal property with label {} because its output id was not defined.", label);
      return Opt.none();
    }

    MetadataField<String> durationField = MetadataField.createDurationMetadataField(metadataField.getInputID(),
            outputID, label, metadataField.isReadOnly(), metadataField.isRequired(), metadataField.getOrder(),
            metadataField.getNamespace());
    Long value = p.get().getEnd().getTime() - p.get().getStart().getTime();
    if (p.get().getEnd().before(p.get().getStart())) {
      throw new IllegalArgumentException("The start date cannot be before the end date. Start: " + p.get().getStart()
              + " End: " + p.get().getEnd());
    }
    durationField.setValue(value.toString());
    return Opt.some(durationField);
  }

  /**
   * Create a metadata field of type String with a single line in the front end.
   *
   * @param inputID
   *          The identifier of the new metadata field
   * @param label
   *          The label of the new metadata field
   * @param readOnly
   *          Define if the new metadata field can be or not edited
   * @param required
   *          Define if the new metadata field is or not required
   * @param isTranslatable
   *          If the field value is not human readable and should be translated before
   * @param collection
   *          If the field has a limited list of possible value, the option should contain this one. Otherwise it should
   *          be none.
   * @param order
   *          The ui order for the new field, 0 at the top and progressively down from there.
   * @return the new metadata field
   */
  public static MetadataField<String> createTextMetadataField(String inputID, Opt<String> outputID, String label,
          boolean readOnly, boolean required, Opt<Boolean> isTranslatable, Opt<Map<String, String>> collection,
          Opt<String> collectionId, Opt<Integer> order, Opt<String> namespace) {
    return createTextLongMetadataField(inputID, outputID, label, readOnly, required, isTranslatable, collection, collectionId, order,
            JsonType.TEXT, namespace);
  }

  /**
   * Create a metadata field of type String with a single line in the front end which can be ordered and filtered.
   *
   * @param inputID
   *          The identifier of the new metadata field
   * @param label
   *          The label of the new metadata field
   * @param readOnly
   *          Define if the new metadata field can be or not edited
   * @param required
   *          Define if the new metadata field is or not required
   * @param isTranslatable
   *          If the field value is not human readable and should be translated before
   * @param collection
   *          If the field has a limited list of possible value, the option should contain this one. Otherwise it should
   *          be none.
   * @param order
   *          The ui order for the new field, 0 at the top and progressively down from there.
   * @return the new metadata field
   */
  public static MetadataField<String> createOrderedTextMetadataField(
      String inputID,
      Opt<String> outputID,
      String label,
      boolean readOnly,
      boolean required,
      Opt<Boolean> isTranslatable,
      Opt<Map<String, String>> collection,
      Opt<String> collectionId,
      Opt<Integer> order,
      Opt<String> namespace) {
    return createTextLongMetadataField(inputID, outputID, label, readOnly, required, isTranslatable, collection, collectionId, order,
        JsonType.ORDERED_TEXT, namespace);
  }


  /**
   * Create a metadata field of type String with many lines in the front end.
   *
   * @param inputID
   *          The identifier of the new metadata field
   * @param label
   *          The label of the new metadata field
   * @param readOnly
   *          Define if the new metadata field can be or not edited
   * @param required
   *          Define if the new metadata field is or not required
   * @param isTranslatable
   *          If the field value is not human readable and should be translated before
   * @param collection
   *          If the field has a limited list of possible value, the option should contain this one. Otherwise it should
   *          be none.
   * @param order
   *          The ui order for the new field, 0 at the top and progressively down from there.
   * @return the new metadata field
   */
  public static MetadataField<String> createTextLongMetadataField(String inputID, Opt<String> outputID, String label,
          boolean readOnly, boolean required, Opt<Boolean> isTranslatable, Opt<Map<String, String>> collection,
          Opt<String> collectionId, Opt<Integer> order, Opt<String> namespace) {
    return createTextLongMetadataField(inputID, outputID, label, readOnly, required, isTranslatable, collection,
            collectionId, order, JsonType.TEXT_LONG, namespace);
  }

  /**
   * Create a metadata field of type String specifying the type for the front end.
   *
   * @param id
   *          The identifier of the new metadata field
   * @param label
   *          The label of the new metadata field
   * @param readOnly
   *          Define if the new metadata field can be or not edited
   * @param required
   *          Define if the new metadata field is or not required
   * @param isTranslatable
   *          If the field value is not human readable and should be translated before
   * @param collection
   *          If the field has a limited list of possible value, the option should contain this one. Otherwise it should
   *          be none.
   * @param order
   *          The ui order for the new field, 0 at the top and progressively down from there.
   * @return the new metadata field
   */
  private static MetadataField<String> createTextLongMetadataField(String inputID, Opt<String> outputID, String label,
          boolean readOnly, boolean required, Opt<Boolean> isTranslatable, Opt<Map<String, String>> collection,
          Opt<String> collectionId, Opt<Integer> order, JsonType jsonType, Opt<String> namespace) {

    Fn<Opt<String>, JValue> stringToJSON = new Fn<Opt<String>, JValue>() {
      @Override
      public JValue apply(Opt<String> value) {
        return v(value.getOr(""));
      }
    };

    Fn<Object, String> jsonToString = new Fn<Object, String>() {
      @Override
      public String apply(Object jsonValue) {
        if (jsonValue == null)
          return "";
        if (!(jsonValue instanceof String)) {
          logger.warn("Value cannot be parsed as String. Expecting type 'String', but received type '{}'.", jsonValue.getClass().getName());
          return null;
        }
        return (String) jsonValue;
      }
    };

    return new MetadataField<>(inputID, outputID, label, readOnly, required, "", isTranslatable, Type.TEXT, jsonType,
            collection, collectionId, stringToJSON, jsonToString, order, namespace);
  }

  /**
   * Turn a map into a {@link JObject} object
   *
   * @param map
   *          the source map
   * @return a new {@link JObject} generated with the map values
   */
  public static JObject mapToJSON(Map<String, String> map) {
    if (map == null) {
      throw new IllegalArgumentException("Map must not be null!");
    }

    List<Field> fields = new ArrayList<>();
    for (Entry<String, String> item : map.entrySet()) {
      fields.add(f(item.getKey(), v(item.getValue(), Jsons.BLANK)));
    }
    return obj(fields);
  }

  /**
   * A convenience function for converting properties values into their java equivalents
   *
   * @param key
   *          The key for the metadata property.
   * @param value
   *          The value for the metadata property.
   */
  public void setValue(String key, String value) {
    switch (key) {
      case CONFIG_COLLECTION_ID_KEY:
        this.collectionID = Opt.some(value);
        break;
      case CONFIG_PATTERN_KEY:
        this.pattern = Opt.some(value);
        break;
      case CONFIG_INPUT_ID_KEY:
        this.inputID = value;
        break;
      case CONFIG_LABEL_KEY:
        this.label = value;
        break;
      case CONFIG_LIST_PROVIDER_KEY:
        this.listprovider = Opt.some(value);
        break;
      case CONFIG_NAMESPACE_KEY:
        this.namespace = Opt.some(value);
        break;
      case CONFIG_ORDER_KEY:
        try {
          Integer orderValue = Integer.parseInt(value);
          this.order = Opt.some(orderValue);
        } catch (NumberFormatException e) {
          logger.warn("Unable to parse order value {} of metadata field {} because:{}",
                  value, this.getInputID(), ExceptionUtils.getStackTrace(e));
          this.order = Opt.none();
        }
        break;
      case CONFIG_OUTPUT_ID_KEY:
        this.outputID = Opt.some(value);
        break;
      case CONFIG_READ_ONLY_KEY:
        this.readOnly = Boolean.valueOf(value);
        break;
      case CONFIG_REQUIRED_KEY:
        this.required = Boolean.valueOf(value);
        break;
      case CONFIG_TYPE_KEY:
        this.type = Type.valueOf(value.toUpperCase());
        break;
      default:
        throw new IllegalArgumentException("Unknown Dublin Core Property Key " + key);
    }
  }

  public Opt<String> getCollectionID() {
    return collectionID;
  }

  public void setCollectionID(Opt<String> collectionID) {
    this.collectionID = collectionID;
  }

  public String getInputID() {
    return inputID;
  }

  public void setInputId(String inputID) {
    this.inputID = inputID;
  }

  public String getLabel() {
    return label;
  }

  public void setLabel(String label) {
    this.label = label;
  }

  public Opt<String> getListprovider() {
    return listprovider;
  }

  public void setListprovider(Opt<String> listprovider) {
    this.listprovider = listprovider;
  }

  public Opt<String> getNamespace() {
    return namespace;
  }

  public void setNamespace(Opt<String> namespace) {
    this.namespace = namespace;
  }

  public Opt<Integer> getOrder() {
    return order;
  }

  public void setOrder(Opt<Integer> order) {
    this.order = order;
  }

  /**
   * @return The outputID if available, inputID if it is missing.
   */
  public String getOutputID() {
    if (outputID.isSome()) {
      return outputID.get();
    } else {
      return inputID;
    }
  }

  public void setOutputID(Opt<String> outputID) {
    this.outputID = outputID;
  }

  public Opt<String> getPattern() {
    return pattern;
  }

  public void setPattern(Opt<String> pattern) {
    this.pattern = pattern;
  }

  public void setReadOnly(boolean readOnly) {
    this.readOnly = readOnly;
  }

  public boolean isReadOnly() {
    return readOnly;
  }

  public boolean isRequired() {
    return required;
  }

  public void setRequired(boolean required) {
    this.required = required;
  }

  public Type getType() {
    return type;
  }

  public void setType(Type type) {
    this.type = type;
  }

  public JsonType getJsonType() {
    return jsonType;
  }

  public void setJsonType(JsonType jsonType) {
    this.jsonType = jsonType;
  }

  public Fn<Object, A> getJsonToValue() {
    return jsonToValue;
  }

  public void setJsonToValue(Fn<Object, A> jsonToValue) {
    this.jsonToValue = jsonToValue;
  }

  public Fn<Opt<A>, JValue> getValueToJSON() {
    return valueToJSON;
  }

  public void setValueToJSON(Fn<Opt<A>, JValue> valueToJSON) {
    this.valueToJSON = valueToJSON;
  }
}
