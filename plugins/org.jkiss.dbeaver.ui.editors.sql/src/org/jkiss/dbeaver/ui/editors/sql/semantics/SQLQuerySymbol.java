/*
 * DBeaver - Universal Database Manager
 * Copyright (C) 2010-2023 DBeaver Corp and others
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jkiss.dbeaver.ui.editors.sql.semantics;

import org.jkiss.dbeaver.model.sql.parser.tokens.SQLTokenType;
import org.jkiss.dbeaver.model.struct.DBSObject;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

enum SQLQuerySymbolClass {
    UNKNOWN(SQLTokenType.T_OTHER),
    CATALOG(SQLTokenType.T_SCHEMA),
    SCHEMA(SQLTokenType.T_SCHEMA),
    TABLE(SQLTokenType.T_TABLE),
    TABLE_ALIAS(SQLTokenType.T_TABLE_ALIAS),
    COLUMN(SQLTokenType.T_COLUMN),
    COLUMN_DERIVED(SQLTokenType.T_COLUMN_DERIVED),
    ERROR(SQLTokenType.T_SEMANTIC_ERROR);
    
    private final SQLTokenType tokenType;
    
    private SQLQuerySymbolClass(SQLTokenType tokenType) {
        this.tokenType = tokenType;
    }
    
    public SQLTokenType getTokenType() {
        return this.tokenType;
    }
}

interface SQLQuerySymbolDefinition {
    SQLQuerySymbolClass getSymbolClass();
}

class SQLQuerySymbolByDbObjectDefinition implements SQLQuerySymbolDefinition {
    private final DBSObject dbObject;
    private final SQLQuerySymbolClass symbolClass;

    public SQLQuerySymbolByDbObjectDefinition(DBSObject dbObject, SQLQuerySymbolClass symbolClass) {
        this.dbObject = dbObject;
        this.symbolClass = symbolClass;
    }
    
    public DBSObject getDbObject() {
        return this.dbObject;
    }

    @Override
    public SQLQuerySymbolClass getSymbolClass() {
        return this.symbolClass;
    }
}

public class SQLQuerySymbol {
    private final String name;
    private final Set<SQLQuerySymbolEntry> entries = new HashSet<>();
    
    private SQLQuerySymbolClass symbolClass = SQLQuerySymbolClass.UNKNOWN;
    private SQLQuerySymbolDefinition definition = null;

    public SQLQuerySymbol(String name) {
        this.name = name;
    }
    
    public String getName() {
        return this.name;
    }

    public SQLQuerySymbolClass getSymbolClass() {
        return this.symbolClass;
    }

    public void setSymbolClass(SQLQuerySymbolClass symbolClass) {
        if (this.symbolClass != SQLQuerySymbolClass.UNKNOWN) {
            throw new UnsupportedOperationException("Symbol already classified");
        } else {
            this.symbolClass = symbolClass;
        }
    }
    
    public Collection<SQLQuerySymbolEntry> getEntries() {
        return this.entries;
    }
    
    public SQLQuerySymbolDefinition getDefinition() {
        return this.definition;
    }
    
    public void setDefinition(SQLQuerySymbolDefinition definition) {
        if (this.definition != null) {
            throw new UnsupportedOperationException("Symbol definition has already been set");
        } else if (definition != null) {
            this.definition = definition;
            this.setSymbolClass(definition.getSymbolClass());
        }
    }
    
    public void registerEntry(SQLQuerySymbolEntry entry) {
        if (!entry.getName().equals(this.name)) {
            throw new UnsupportedOperationException("Cannot treat symbols '" + entry.getName() + "' as an instance of '" + this.name + "'");
        }
        
        this.entries.add(entry);
    }
    
    public SQLQuerySymbol merge(SQLQuerySymbol other) { // TODO merge multiple definitions and check for symbolClass
        if (!other.name.equals(this.name)) {
            throw new UnsupportedOperationException("Cannot treat different symbols as one ('" + this.name + "' and '" + other.name + "')");
        }
        
        SQLQuerySymbol result = new SQLQuerySymbol(this.name);
        result.entries.addAll(this.entries);
        result.entries.addAll(other.entries);
        result.entries.forEach(e -> SQLQuerySymbolEntry.updateSymbol(e, result));
        return this;
    }
    
    @Override
    public String toString() {
        return super.toString() + "[" + this.name + "]";
    }
}
