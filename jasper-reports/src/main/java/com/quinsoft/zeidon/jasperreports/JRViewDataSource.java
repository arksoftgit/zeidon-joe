/**
    This file is part of the Zeidon Java Object Engine (Zeidon JOE).

    Zeidon JOE is free software: you can redistribute it and/or modify
    it under the terms of the GNU Lesser General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    Zeidon JOE is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Lesser General Public License for more details.

    You should have received a copy of the GNU Lesser General Public License
    along with Zeidon JOE.  If not, see <http://www.gnu.org/licenses/>.

    Copyright 2009-2015 QuinSoft
 */
package com.quinsoft.zeidon.jasperreports;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.sf.jasperreports.engine.JRDataSource;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JRField;
import net.sf.jasperreports.engine.JRRewindableDataSource;
import net.sf.jasperreports.engine.JasperReport;

import org.apache.commons.lang3.StringUtils;

import com.quinsoft.zeidon.AttributeInstance;
import com.quinsoft.zeidon.EntityCursor;
import com.quinsoft.zeidon.View;
import com.quinsoft.zeidon.ZeidonException;
import com.quinsoft.zeidon.objectdefinition.EntityDef;

/**
 * A wrapper to allow a Zeidon View to be used as a JasperReports DataSource.
 *
 */
public class JRViewDataSource implements JRDataSource, JRRewindableDataSource
{
    // "((\\w*)(\\.))?(\\w*)\\s*(\\s*\\(\\s*(\\w*)\\s*\\)\\s*)?\\s*(\\s*\\>(.*))?"
    private final static String REGEX = "((\\w*)(\\.))?"                  // Optional entity name--word followed by period.
                                      + "(\\w*)"                          // Required attribute name.
                                      + "\\s*"                            // Optional whitespace
                                      + "(\\s*\\(\\s*(\\w*)\\s*\\)\\s*)?" // Optional context name--word inside parens.
                                      + "\\s*"                            // Optional whitespace
                                      + "(\\s*\\>(.*))?";                 // Optional default value--all chars after ">".
    private final static Pattern FIELD_PATTERN = Pattern.compile( REGEX );

    final private View      view;

    /**
     * This entity is the entity that we loop on.
     */
    final private EntityDef topEntity;

    final private JasperReport jasperReport;
    
    private boolean cursorSet;

    /**
     * Empty constructor.  Intended to be called from a DataSourceProvider when testing
     * the provider from Jasper's Report Studio.
     */
    public JRViewDataSource( )
    {
        view = null;
        topEntity = null;
        jasperReport = null;
    }

    public JRViewDataSource( View view, JasperReport jasperReport )
    {
        this.view = view;
        view.log().debug( "Creating a JRViewDataSource" );
        this.jasperReport = jasperReport;
        this.topEntity = getReportRoot();
        
        try
        {
            moveFirst();
        }
        catch ( JRException e )
        {
            throw ZeidonException.wrapException( e );
        }
    }

    protected EntityDef getReportRoot()
    {
        String reportRoot = jasperReport.getProperty( "com.quinsoft.zeidon.reportRoot" );
        view.log().info( "com.quinsoft.zeidon.reportRoot = %s", reportRoot );
        if ( StringUtils.isBlank( reportRoot ) )
            return view.getLodDef().getRoot();

        return view.getLodDef().getEntityDef( reportRoot );
    }

    /* (non-Javadoc)
     * @see net.sf.jasperreports.engine.JRRewindableDataSource#moveFirst()
     */
    @Override
    public void moveFirst() throws JRException
    {
        cursorSet = false;
    }

    /* (non-Javadoc)
     * @see net.sf.jasperreports.engine.JRDataSource#getFieldValue(net.sf.jasperreports.engine.JRField)
     */
    @Override
    public Object getFieldValue( JRField field ) throws JRException
    {
        Object value = get( field );
        view.log().info( "Get field %s ", field.getName() );
        return value;
    }

    private Object get( JRField field ) throws JRException
    {
        String fieldName = field.getName();
        Matcher m = FIELD_PATTERN.matcher( fieldName );
        if ( ! m.matches() )
            throw new ZeidonException( "FieldName value doesn't match expected pattern of "
                                     + "[ entity-name. ] attribute-name [ ( context-name ) ] [ > default ]" )
                      .appendMessage( "FieldName = %s", fieldName );

        String entityName = m.group( 2 );
        EntityDef entityDef = topEntity;
        if ( ! StringUtils.isBlank( entityName ) )
            entityDef = view.getLodDef().getEntityDef( entityName );
        EntityCursor cursor = view.cursor( entityDef );

        String defaultValue = m.group( 8 );
        if ( defaultValue == null )
            defaultValue = "";

        if ( cursor.isNull() )
            return defaultValue;

        String attributeName = m.group( 4 );
        AttributeInstance attribute = cursor.getAttribute( attributeName );
        if ( attribute.isNull() )
            return defaultValue;

        String contextName = m.group( 6 );
        return attribute.getString( contextName );
    }

    /* (non-Javadoc)
     * @see net.sf.jasperreports.engine.JRDataSource#next()
     */
    @Override
    public boolean next() throws JRException
    {
        if ( ! cursorSet )
        {
            cursorSet = true;
            return view.cursor( topEntity ).setFirstWithinOi().isSet();
        }

        boolean rc = view.cursor( topEntity ).setNextContinue().isSet();
        if ( rc )
            view.log().info( "next() found %s", view.cursor( topEntity ).getEntityInstance() );

        return rc;
    }
}
