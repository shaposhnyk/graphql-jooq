package com.shaposhnyk

import graphql.Scalars
import graphql.schema.*
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.TableField
import org.jooq.impl.DSL
import org.jooq.impl.TableImpl
import java.util.stream.Collectors
import java.util.stream.Stream
import kotlin.reflect.KType
import kotlin.reflect.jvm.reflect

class EntityBuilder<T : Record>(val tableDef: TableImpl<T>, val ctxSupplier: () -> DSLContext) {
    val fieldDefs = mutableListOf<GraphQLFieldDefinition>()
    val fieldDefsByName = mutableMapOf<String, TableField<T, *>>()
    val conditionByName = mutableMapOf<String, Condition>()

    fun <R> field(name: String, field: TableField<T, R?>, decorator: (R?) -> Any?): EntityBuilder<T> {
        val gqlType = mapToScalar(field.type, decorator.reflect()?.returnType?.javaClass)

        val fieldDef = GraphQLFieldDefinition.newFieldDefinition()
            .name(name)
            .type(gqlType)
            .description(field.comment)
            .dataFetcher { env ->
                val parent = env.getSource<Record>()
                val value = field.getValue(parent)
                decorator(value)
            }
            .build()

        fieldDefs.add(fieldDef)
        fieldDefsByName[name] = field

        return this;
    }

    fun field(name: String, field: TableField<T, *>): EntityBuilder<T> = field(name, field) { it }

    fun field(field: TableField<T, *>): EntityBuilder<T> = field(field.name, field)

    fun buildObjectType(): GraphQLObjectType.Builder {
        return GraphQLObjectType.newObject()
            .name(tableDef.name)
            .description(tableDef.comment)
            .fields(fieldDefs)
    }

    fun buildObjectTypeRef(): GraphQLTypeReference {
        return GraphQLTypeReference(tableDef.name)
    }

    fun buildFetcher(): DataFetcher<Iterable<T>> = buildFetcher(DSL.noCondition())

    fun buildFetcher(filteringCondition: Condition): DataFetcher<Iterable<T>> {
        return DataFetcher {
            fetchEntity(it, filteringCondition)
                .collect(Collectors.toList())
        }
    }

    private fun fetchEntity(env: DataFetchingEnvironment, condition: Condition): Stream<T> {
        val ctx: DSLContext = getDslContext()
        val fields = extractSelectedFields(env)

        return ctx.select(fields)
            .from(tableDef)
            .where(condition)
            .fetchStream()
            .map { it as T }
    }

    private fun getDslContext(): DSLContext = ctxSupplier()

    private fun extractSelectedFields(env: DataFetchingEnvironment): Set<TableField<T, *>?> {
        val fieldNames = env.selectionSet.get().keys.toTypedArray()
        return fieldNames.map { fieldDefsByName[it] }
            .toSet()
    }

    fun buildObjectListType() = GraphQLList.list(buildObjectType().build())

    private fun <R> mapToScalar(type: Class<R?>, decoratorClass: Class<KType>?): GraphQLScalarType {
        if ("".javaClass == type) {
            return Scalars.GraphQLString
        } else if (java.lang.Integer.valueOf(0).javaClass == type) {
            return Scalars.GraphQLInt
        }
        TODO("not supported")
    }

    companion object {
        fun <T : Record> newBuilder(objectType: TableImpl<T>, supplier: () -> DSLContext): EntityBuilder<T> {
            return EntityBuilder(objectType, supplier)
        }
    }
}
