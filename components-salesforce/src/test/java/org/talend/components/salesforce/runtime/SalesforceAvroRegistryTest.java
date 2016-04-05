package org.talend.components.salesforce.runtime;

import com.sforce.soap.partner.DescribeSObjectResult;
import com.sforce.soap.partner.Field;
import com.sforce.soap.partner.FieldType;
import org.apache.avro.Schema;
import org.junit.Test;
import org.talend.daikon.avro.SchemaConstants;
import org.talend.daikon.avro.util.AvroTypes;
import org.talend.daikon.avro.util.AvroUtils;

import java.math.BigDecimal;
import java.util.Date;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.talend.daikon.avro.SchemaConstants.TALEND_COLUMN_DEFAULT;

/**
 * Unit tests for the {@link SalesforceAvroRegistry}.
 */
@SuppressWarnings("nls")
public class SalesforceAvroRegistryTest {

    private static final SalesforceAvroRegistry sRegistry = SalesforceAvroRegistry.get();

    /**
     * Tests that the {@link SalesforceAvroRegistry} has added support to get a {@link Schema} that describes
     * {@link DescribeSObjectResult} objects.
     */
    @Test
    public void testInferSchemaDescribeSObjectResult() {
        Schema s;
        // Setup.
        {
            Field booleanField = new Field();
            booleanField.setName("valid");
            booleanField.setType(FieldType._boolean);

            Field defaultField = new Field();
            defaultField.setName("def");
            defaultField.setType(FieldType._boolean);
            defaultField.setDefaultValueFormula(Boolean.TRUE.toString());

            Field dateField = new Field();
            dateField.setName("date");
            dateField.setType(FieldType.date);

            DescribeSObjectResult dsor = new DescribeSObjectResult();
            dsor.setName("MySObjectRecord");
            dsor.setFields(new Field[] { booleanField, defaultField, dateField });
            s = sRegistry.inferSchema(dsor);
        }

        assertThat(s.getType(), is(Schema.Type.RECORD));
        assertThat(s.getName(), is("MySObjectRecord"));
        assertThat(s.getFields(), hasSize(3));
        assertThat(s.getObjectProps().keySet(), empty());

        // Check out the field.
        Schema.Field f = s.getFields().get(0);
        assertThat(f.name(), is("valid"));
        assertThat(f.schema().getType(), is(Schema.Type.BOOLEAN));
        assertThat(f.schema().getObjectProps().keySet(), empty());

        f = s.getField("def");
        assertThat(f.name(), is("def"));
        assertThat(f.schema().getType(), is(Schema.Type.BOOLEAN));
        assertThat(f.getObjectProps().keySet(),containsInAnyOrder(TALEND_COLUMN_DEFAULT));
        assertThat(f.getProp(TALEND_COLUMN_DEFAULT), is(Boolean.TRUE.toString()));

        f = s.getField("date");
        assertThat(f.name(), is("date"));
        assertTrue(AvroTypes.isSameType(f.schema(), AvroTypes._date()));
        assertThat(f.getObjectProps().keySet(),containsInAnyOrder(SchemaConstants.TALEND_COLUMN_PATTERN));
        assertThat(f.getProp(SchemaConstants.TALEND_COLUMN_PATTERN), is("yyyy-MM-dd"));

    }

    /**
     * Tests that the {@link SalesforceAvroRegistry} has added support to get a {@link Schema} that describes
     * {@link Field} objects.
     */
    @Test
    public void testInferSchemaField() {
        // Test boolean extensively.
        Field f = new Field();
        f.setType(FieldType._boolean);

        Schema s = sRegistry.inferSchema(f);
        assertThat(s.getType(), is(Schema.Type.BOOLEAN));
        // The properties injected into boolean.
        assertThat(s.getObjectProps().keySet(), empty());

        // The same thing if nullable.
        f.setNillable(true);
        s = sRegistry.inferSchema(f);
        assertThat(s.getType(), is(Schema.Type.UNION));
        assertThat(s.getTypes(), hasSize(2));
        assertThat(s.getObjectProps().keySet(), empty());
        s = AvroUtils.unwrapIfNullable(s);
        assertThat(s.getType(), is(Schema.Type.BOOLEAN));
        assertThat(s.getObjectProps().keySet(), empty());

        f = new Field();
        f.setType(FieldType._int);
        s = sRegistry.inferSchema(f);
        assertThat(s.getType(), is(Schema.Type.INT));

        f.setType(FieldType.date);
        s = sRegistry.inferSchema(f);
        assertThat(s.getType(), is(Schema.Type.LONG));
        assertThat(s.getProp(SchemaConstants.JAVA_CLASS_FLAG), is(Date.class.getCanonicalName()));

        f.setType(FieldType.datetime);
        s = sRegistry.inferSchema(f);
        assertThat(s.getType(), is(Schema.Type.LONG));
        assertThat(s.getProp(SchemaConstants.JAVA_CLASS_FLAG), is(Date.class.getCanonicalName()));

        f.setType(FieldType._double);
        s = sRegistry.inferSchema(f);
        assertThat(s.getType(), is(Schema.Type.DOUBLE));

        f.setType(FieldType.currency);
        f.setPrecision(8);
        f.setScale(5);
        s = sRegistry.inferSchema(f);
        assertThat(s.getType(), is(Schema.Type.STRING));
        assertThat(s.getProp(SchemaConstants.JAVA_CLASS_FLAG), is(BigDecimal.class.getCanonicalName()));
//        assertThat(s.getLogicalType(), is((LogicalType) LogicalTypes.decimal(8, 5)));
    }
}
