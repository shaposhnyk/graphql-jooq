package com.shaposhnyk

import graphql.Scalars
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLScalarType
import org.jooq.Record
import org.jooq.TableField
import org.jooq.impl.TableImpl
import kotlin.reflect.KType
import kotlin.reflect.jvm.reflect

class EntityBuilder<T : Record>(val tableDef: TableImpl<T>) {
    val fieldDefs = mutableListOf<GraphQLFieldDefinition>();

    fun <R> field(name: String, field: TableField<T, R?>, decorator: (R?) -> Any?): EntityBuilder<T> {
        val gqlType = mapToScalar(field.type, decorator.reflect()?.returnType?.javaClass)

        fieldDefs.add(
            GraphQLFieldDefinition.newFieldDefinition()
                .name(name)
                .type(gqlType)
                .description(field.comment)
                .dataFetcher { env ->
                    val parent = env.getSource<Record>()
                    val value = field.getValue(parent)
                    decorator(value)
                }
                .build()
        )

        return this;
    }

    private fun <R> mapToScalar(type: Class<R?>, decoratorClass: Class<KType>?): GraphQLScalarType {
        if (String.javaClass == type) {
            return Scalars.GraphQLString;
        }
        TODO("not supported")
    }

    fun field(name: String, field: TableField<T, *>): EntityBuilder<T> = field(name, field) { it }

    fun field(field: TableField<T, String>): EntityBuilder<T> = field(field.name, field)

    fun build(): GraphQLObjectType.Builder {
        return GraphQLObjectType.newObject()
            .name(tableDef.name)
            .description(tableDef.comment)
            .fields(fieldDefs)
    }

    companion object {
        fun <T : Record> newBuilder(objectType: TableImpl<T>): EntityBuilder<T> {
            return EntityBuilder(objectType)
        }

    }

}
