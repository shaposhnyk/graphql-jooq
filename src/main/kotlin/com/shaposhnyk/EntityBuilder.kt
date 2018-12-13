package com.shaposhnyk

import graphql.Scalars
import graphql.schema.*
import org.jooq.*
import org.jooq.impl.DSL
import org.jooq.impl.TableImpl
import java.util.stream.Collectors
import java.util.stream.Stream
import kotlin.reflect.KType
import kotlin.reflect.jvm.reflect

class EntityBuilder<T : Record>(val name: String, val tableDef: TableImpl<T>, val ctxSupplier: () -> DSLContext) {
    val fieldDefs = mutableListOf<GraphQLFieldDefinition>()
    val fieldDefsByName = mutableMapOf<String, List<TableField<T, *>>>()
    val conditionByName = mutableMapOf<String, Condition>()

    fun fieldOf(builderFactory: (JooqFieldBuilder<T>) -> JooqFieldBuilder<T>): EntityBuilder<T> {
        val initBuilder = JooqFieldBuilder<T>()
            .fetchEntityWith { it.getSource<Record>() } // default fetcher
            .withType(Scalars.GraphQLString) // default type

        val builder = builderFactory(initBuilder);
        val dataFetcher = builder.buildFetcher()

        val fieldDef = GraphQLFieldDefinition.newFieldDefinition()
            .name(builder.gqlName)
            .type(builder.gqlType)
            .description(builder.desciption)
            .dataFetcher(dataFetcher)
            .build()

        fieldDefsByName[fieldDef.name] = builder.sourceColumns.toList()
        return addFieldDef(fieldDef)
    }

    fun <R> field(name: String, field: TableField<T, R?>, decorator: (R?) -> Any?): EntityBuilder<T> {
        val gqlType: GraphQLScalarType = mapToScalar(field.type, decorator.reflect()?.returnType?.javaClass)

        return fieldOf { filedBuilder ->
            filedBuilder
                .withName(name)
                .withType(gqlType)
                .withDescription(field.comment)
                .withSourceColumn(field)
                .extractFrom { fieldValueOrNull(field, it) }
                .decorate { decorator(it as R?) }
        }
    }

    fun field(name: String, field: TableField<T, *>): EntityBuilder<T> = field(name, field) { it }

    fun field(field: TableField<T, *>): EntityBuilder<T> = field(field.name.toLowerCase(), field)

    fun relOneToMany(
        name: String,
        relatedEntity: EntityBuilder<*>,
        filterKey: TableField<Record, String>,
        atOnce: Boolean = false
    ): EntityBuilder<T> {
        val gqlType = relatedEntity.buildObjectListType()
        val comment = "List of ${name} related to ${tableDef.name}"
        val cols = tableDef.primaryKey.fields

        return fieldOf { filedBuilder ->
            filedBuilder
                .withName(name)
                .withType(gqlType)
                .withDescription(comment)
                .withSourceColumns(cols)
                .extractFromContext { env, parent ->
                    val keyValue: Any = cols.iterator().next().getValue(parent)
                    val value = relatedEntity.buildFetcher(filterKey.eq(keyValue as String)).get(env)
                    value
                }
        }
    }

    fun relOneToMany(name: String, relatedEntity: EntityBuilder<*>, atOnce: Boolean = false): EntityBuilder<T> {
        val matchedTables: List<ForeignKey<*, T>> = tableDef.primaryKey.references
            .filter { it.table.equals(relatedEntity.tableDef) }
            .toList()

        if (matchedTables.size != 1) {
            throw IllegalArgumentException("Unknown relation between ${tableDef} and ${relatedEntity.tableDef}")
        }

        val relationKey: TableField<Record, String> =
            matchedTables.iterator().next().fields.iterator().next() as TableField<Record, String>
        return relOneToMany(name, relatedEntity, relationKey)
    }

    fun relOneToMany(relatedEntity: EntityBuilder<*>) =
        relOneToMany(relatedEntity.name.toLowerCase() + "List", relatedEntity)

    fun relOneToManyAtOnce(relatedEntity: EntityBuilder<*>) =
        relOneToMany(relatedEntity.name.toLowerCase() + "List", relatedEntity, atOnce = true)


    fun addFieldDef(fieldDef: GraphQLFieldDefinition): EntityBuilder<T> {
        fieldDefs.add(fieldDef)
        return this
    }

    fun buildObjectType(): GraphQLObjectType.Builder {
        return GraphQLObjectType.newObject()
            .name(name)
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
        val fieldNames: Array<String> = env.selectionSet.get().keys.toTypedArray()
        return fieldNames.flatMap { name -> fieldDefsByName[name]?.asIterable() ?: emptyList() }
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

    private fun fieldValueOrNull(field: TableField<T, *>, parent: Record?): Any? {
        if (parent == null) {
            return null
        }
        return field.getValue(parent)
    }

    companion object {
        fun <T : Record> newBuilder(objectType: TableImpl<T>, supplier: () -> DSLContext): EntityBuilder<T> {
            return EntityBuilder(objectType.name, objectType, supplier)
        }
    }
}
