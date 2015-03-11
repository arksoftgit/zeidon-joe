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

    Copyright 2009-2014 QuinSoft
 */
package com.quinsoft.zeidon.standardoe;

import com.quinsoft.zeidon.GeneratedKey;
import com.quinsoft.zeidon.ZeidonException;

/**
 * Standard implementation of a GeneratedKey.
 */
public class GeneratedKeyImpl implements GeneratedKey
{
    private final String value;

    public GeneratedKeyImpl( String value )
    {
        this.value = value;
    }

    /* (non-Javadoc)
     * @see com.quinsoft.zeidon.GeneratedKey#isNull()
     */
    @Override
    public boolean isNull()
    {
        return value == null;
    }

    @Override
    public String getString()
    {
        if ( isNull() )
            throw new ZeidonException( "Attempting to convert null GeneratedKey to a string" );

        return value;
    }

    @Override
    public String toString()
    {
        if ( isNull() )
            return "Key: (null)";

        return "Key: " + value;
    }
}