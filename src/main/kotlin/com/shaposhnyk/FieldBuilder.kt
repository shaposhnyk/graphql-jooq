package com.shaposhnyk

import graphql.schema.DataFetchingEnvironment
import graphql.schema.GraphQLOutputType
import org.jooq.Record
import org.jooq.TableField
import org.slf4j.LoggerFactory

class FieldBuilder<T : Record>(
    var gqlName: String? = null,
    var gqlType: GraphQLOutputType? = null,
    var sourceColumns: MutableList<TableField<T, *>> = mutableListOf(),
    var desciption: String = "",
    var entityFetcher: (DataFetchingEnvironment) -> Record? = { _ -> null },
    var extractor: (DataFetchingEnvironment, Record?) -> Any? = { _, p -> p },
    var decorator: (Any?) -> Any? = { v -> v }
) {
    fun withName(name: String): FieldBuilder<T> {
        this.gqlName = name
        return this;
    }

    fun withDescription(desciption: String): FieldBuilder<T> {
        this.desciption = desciption
        return this;
    }

    fun withType(type: GraphQLOutputType): FieldBuilder<T> {
        this.gqlType = type
        return this;
    }

    fun withSourceColumn(col: TableField<T, *>): FieldBuilder<T> {
        this.sourceColumns.add(col)
        return this;
    }

    fun withSourceColumns(col1: TableField<T, *>, col2: TableField<T, *>): FieldBuilder<T> {
        return withSourceColumn(col1).withSourceColumn(col2)
    }

    fun withSourceColumns(cols: List<TableField<T, *>>): FieldBuilder<T> {
        this.sourceColumns.addAll(cols)
        return this
    }

    fun extractFrom(extractor: (Record?) -> Any?): FieldBuilder<T> {
        this.extractor = { _, parent -> extractor(parent) }
        return this
    }

    fun extractFromContext(extractor: (DataFetchingEnvironment, Record?) -> Any?): FieldBuilder<T> {
        this.extractor = extractor
        return this
    }

    fun fetchParentWith(entityFetcher: (DataFetchingEnvironment) -> Record?): FieldBuilder<T> {
        this.entityFetcher = entityFetcher
        return this
    }

    fun decorate(decorator: (Any?) -> Any?): FieldBuilder<T> {
        this.decorator = decorator
        return this
    }

    fun buildFetcher(): (DataFetchingEnvironment) -> Any? {
        return { env ->
            val parent = this.entityFetcher(env)
            logger.debug("{} - fetched parent: {}", gqlName, parent)
            val value = extractor(env, parent)
            logger.debug("{} - fetched value: {}", gqlName, value)
            val decoratedValue = this.decorator(value)
            decoratedValue
        }
    }

    companion object {
        val logger = LoggerFactory.getLogger(FieldBuilder::class.java)
    }
}