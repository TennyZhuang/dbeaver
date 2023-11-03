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

import java.util.*;

class SQLQuerySelectionModel {

    // TODO bring cte here apparently
    
    private final HashSet<SQLQuerySymbolEntry> symbolEntries;
    private final SQLQueryRowsSource resultSource;
    
    public SQLQuerySelectionModel(SQLQueryRowsSource resultSource, HashSet<SQLQuerySymbolEntry> symbolEntries) {
        this.resultSource = resultSource;
        this.symbolEntries = symbolEntries;
    }

    public Collection<SQLQuerySymbolEntry> getAllSymbols() {
        return symbolEntries;
    }

    public void propagateContex(SQLQueryDataContext dataContext, SQLQueryRecognitionContext recognitionContext) {
        SQLQueryDataContext resultContext = resultSource.propagateContext(dataContext, recognitionContext);
    }
}
