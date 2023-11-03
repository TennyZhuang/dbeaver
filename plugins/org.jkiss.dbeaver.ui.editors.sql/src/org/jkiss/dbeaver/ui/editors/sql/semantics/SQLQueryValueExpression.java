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

import java.util.List;

abstract class SQLQueryValueExpression {
    
    public SQLQuerySymbol getColumnNameIfTrivialExpression() {
        return null;
    }

    abstract void propagateContext(SQLQueryDataContext context, SQLQueryRecognitionContext statistics);
}

class SQLQuerySubqueryExpression extends SQLQueryValueExpression {
    private final SQLQueryRowsSource source;

    public SQLQuerySubqueryExpression(SQLQueryRowsSource source) {
        this.source = source;
    }
    
    @Override
    void propagateContext(SQLQueryDataContext context, SQLQueryRecognitionContext statistics) {
        this.source.propagateContext(context, statistics);
    }
}

class SQLQueryFlattenedExpression extends SQLQueryValueExpression {
    private final List<SQLQueryValueExpression> operands;

    public SQLQueryFlattenedExpression(List<SQLQueryValueExpression> operands) {
        this.operands = operands;
    }
    
    @Override
    void propagateContext(SQLQueryDataContext context, SQLQueryRecognitionContext statistics) {
        this.operands.forEach(opnd -> opnd.propagateContext(context, statistics));
    }
}

class SQLQueryColumnReferenceExpression extends SQLQueryValueExpression {
    private final SQLQueryQualifiedName tableName;
    private final SQLQuerySymbolEntry columnName;

    public SQLQueryColumnReferenceExpression(SQLQuerySymbolEntry columnName) {
        this.tableName = null;
        this.columnName = columnName;
    }

    public SQLQueryColumnReferenceExpression(SQLQueryQualifiedName tableName, SQLQuerySymbolEntry columnName) {
        this.tableName = tableName;
        this.columnName = columnName;
    }
    
    @Override
    public SQLQuerySymbol getColumnNameIfTrivialExpression() {
        return this.columnName.getSymbol();
    }
    
    void propagateColumnDefinition(SQLQuerySymbolDefinition columnDef, SQLQueryRecognitionContext statistics) { // TODO consider ambiguity
        if (columnDef != null) {
            this.columnName.setDefinition(columnDef);
        } else {
            this.columnName.getSymbol().setSymbolClass(SQLQuerySymbolClass.ERROR);
            statistics.appendError(this.columnName, "Column not found in dataset");
        }
    }
    
    @Override
    void propagateContext(SQLQueryDataContext context, SQLQueryRecognitionContext statistics) {
        if (this.tableName != null) {
            SourceResolutionResult rr = context.resolveSource(this.tableName.toListOfStrings());
            if (rr != null) {
                this.tableName.setDefinition(rr);
                SQLQuerySymbolDefinition columnDef = rr.source.getDataContext().resolveColumn(this.columnName.getName());
                this.propagateColumnDefinition(columnDef, statistics);
            } else {
                this.tableName.setSymbolClass(SQLQuerySymbolClass.ERROR);
                statistics.appendError(this.tableName.entityName, "Table or subquery not found");
            }
        } else {
            this.propagateColumnDefinition(context.resolveColumn(this.columnName.getName()), statistics);
        }
    }
}
