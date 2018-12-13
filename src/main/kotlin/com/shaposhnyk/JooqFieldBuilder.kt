package com.shaposhnyk

import graphql.schema.DataFetchingEnvironment
import graphql.schema.GraphQLOutputType
import org.jooq.Record
import org.jooq.TableField

class JooqFieldBuilder<T : Record>(
    var gqlName: String? = null,
    var gqlType: GraphQLOutputType? = null,
    var sourceColumns: MutableList<TableField<T, *>> = mutableListOf(),
    var desciption: String = "",
    var entityFetcher: (DataFetchingEnvironment) -> Record? = { _ -> null },
    var extractor: (DataFetchingEnvironment, Record?) -> Any? = { _, p -> p },
    var decorator: (Any?) -> Any? = { v -> v }
) {
    fun withName(name: String): JooqFieldBuilder<T> {
        this.gqlName = name
        return this;
    }

    fun withDescription(name: String): JooqFieldBuilder<T> {
        this.desciption = desciption
        return this;
    }

    fun withType(type: GraphQLOutputType): JooqFieldBuilder<T> {
        this.gqlType = type
        return this;
    }

    fun withSourceColumn(col: TableField<T, *>): JooqFieldBuilder<T> {
        this.sourceColumns.add(col)
        return this;
    }

    fun withSourceColumns(col1: TableField<T, *>, col2: TableField<T, *>): JooqFieldBuilder<T> {
        return withSourceColumn(col1).withSourceColumn(col2)
    }

    fun withSourceColumns(cols: List<TableField<T, *>>): JooqFieldBuilder<T> {
        this.sourceColumns.addAll(cols)
        return this;
    }

    fun extractFrom(extractor: (Record?) -> Any?): JooqFieldBuilder<T> {
        this.extractor = { _, parent -> extractor(parent) }
        return this;
    }

    fun extractFromContext(extractor: (DataFetchingEnvironment, Record?) -> Any?): JooqFieldBuilder<T> {
        this.extractor = extractor;
        return this;
    }

    fun fetchEntityWith(entityFetcher: (DataFetchingEnvironment) -> Record?): JooqFieldBuilder<T> {
        this.entityFetcher = entityFetcher
        return this;
    }

    fun decorate(decorator: (Any?) -> Any?): JooqFieldBuilder<T> {
        this.decorator = decorator
        return this
    }

    fun buildFetcher(): (DataFetchingEnvironment) -> Any? {
        return { env ->
            val parent = this.entityFetcher(env)
            val value = this.extractor(env, parent)
            val decoratedValue = this.decorator(value)
            decoratedValue
        }
    }
}