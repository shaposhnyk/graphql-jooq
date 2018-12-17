package com.shaposhnyk

import graphql.Scalars
import graphql.schema.*
import org.jooq.*
import org.jooq.impl.DSL
import org.jooq.impl.TableImpl
import org.slf4j.LoggerFactory
import java.util.stream.Collectors
import java.util.stream.Stream
import kotlin.reflect.KType
import kotlin.reflect.jvm.reflect

class EntityBuilder<T : Record>(
    val name: String,
    val tableDef: TableImpl<T>,
    val ctxSupplier: () -> DSLContext
) {
    val fieldDefs = mutableListOf<GraphQLFieldDefinition>()
    val fieldDefsByName = mutableMapOf<String, List<TableField<T, *>>>()

    val argDefs = mutableListOf<GraphQLArgument>()
    val filterArgsByName = mutableMapOf<String, (Any?) -> Condition>()

    fun fieldOf(builderFactory: (FieldBuilder<T>) -> FieldBuilder<T>): EntityBuilder<T> {
        val initBuilder = FieldBuilder<T>()
            .fetchParentWith { it.getSource<Record>() } // default fetcher
            .withType(Scalars.GraphQLString) // default type

        val builder = builderFactory(initBuilder)
        val fieldDef = builder.buildFieldDefinition()
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

    /**
     * Field with default (EQ) filter
     */
    fun fieldAndFilter(name: String, field: TableField<T, *>) = field(name, field)
        .filterEq(name, field)

    private fun filter(name: String, field: TableField<T, *>): GraphQLArgument.Builder {
        return GraphQLArgument.Builder()
            .name(name)
            .type(mapToScalar(field.type, null))
            .defaultValue(null)
    }

    /**
     * Equality filter on a given column
     */
    fun filterEq(name: String, field: TableField<T, *>, gqlName: String? = null): EntityBuilder<T> {
        val arg = filter(name, field)
            .description("filter items by ${gqlName ?: name} == <value>")
            .defaultValue(null)
            .build()

        return addFilter(arg) { downcast(field).eq(it) }
    }

    /**
     * Equality filter on a given column
     */
    fun filterContains(name: String, field: TableField<T, *>, gqlName: String? = null): EntityBuilder<T> {
        val arg = filter(name, field)
            .description("filter items by ${gqlName ?: name} contains <value>")
            .defaultValue(null)
            .build()

        return addFilter(arg) { downcast(field).contains(it) }
    }

    /**
     * Equality filter on a given column
     */
    fun filterNotEq(name: String, field: TableField<T, *>, gqlName: String? = null): EntityBuilder<T> {
        val arg = filter(name, field)
            .description("filter items by ${gqlName ?: name} != <value>")
            .defaultValue(null)
            .build()

        return addFilter(arg) { downcast(field).ne(it) }
    }

    private fun downcast(field: TableField<T, *>): TableField<T, Any?> {
        return field as TableField<T, Any?>
    }

    private fun addFilter(arg: GraphQLArgument, filterFactory: (Any?) -> Condition): EntityBuilder<T> {
        argDefs.add(arg)
        filterArgsByName[arg.name] = filterFactory
        return this
    }

    fun field(name: String, field: TableField<T, *>): EntityBuilder<T> = field(name, field) { it }

    fun field(field: TableField<T, *>): EntityBuilder<T> = field(field.name.toLowerCase(), field)

    fun relOneToMany(
        name: String,
        relatedEntity: EntityBuilder<*>,
        filterKey: List<TableField<out Record, out Any>>
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
                    val filterConditions = filterKey.map { keyFieldAny: TableField<out Record, out Any> ->
                        val keyField = keyFieldAny as TableField<out Record, Any>
                        keyField.eq(keyField.get(parent))
                    }.toList()
                    val allFilterCondtions = DSL.and(filterConditions)
                    val value = relatedEntity.buildFetcher(allFilterCondtions).get(env)
                    value
                }
        }
    }

    fun relOneToMany(name: String, relatedEntity: EntityBuilder<*>): EntityBuilder<T> {
        val matchedTables: List<ForeignKey<*, T>> = tableDef.primaryKey.references
            .filter { it.table.equals(relatedEntity.tableDef) }
            .toList()

        if (matchedTables.size != 1) {
            throw IllegalArgumentException("Unknown relation between ${tableDef} and ${relatedEntity.tableDef}")
        }

        val relationKey = matchedTables.iterator().next().fields
        return relOneToMany(name, relatedEntity, relationKey)
    }

    fun relOneToMany(relatedEntity: EntityBuilder<*>) =
        relOneToMany(relatedEntity.name.toLowerCase() + "List", relatedEntity)

    fun relOneToManyAtOnce(
        relatedEntity: EntityBuilder<*>
    ): EntityBuilder<T> {
        val gqlType = relatedEntity.buildObjectListType()
        val comment = "List of ${name} related to ${tableDef.name}"
        val cols = tableDef.primaryKey.fields

        val matchedTables: List<ForeignKey<*, T>> = tableDef.primaryKey.references
            .filter { it.table.equals(relatedEntity.tableDef) }
            .toList()

        if (matchedTables.size != 1) {
            throw IllegalArgumentException("Unknown relation between ${tableDef} and ${relatedEntity.tableDef}")
        }

        val filterKey = matchedTables.iterator().next().fields
        val joinFetcher = relatedEntity.buildJoinFetcher(tableDef, DSL.noCondition(), filterKey)
        val fullRelatedEntityFetcher = MemoizingFetcher.of(joinFetcher)

        return fieldOf { filedBuilder ->
            filedBuilder
                .withName(relatedEntity.name.toLowerCase() + "List")
                .withType(gqlType)
                .withDescription(comment)
                .withSourceColumns(cols)
                .extractFromContext { env, parent ->
                    val filteringFetcher = FilteringFetcher.of(fullRelatedEntityFetcher, filterKey.toList())
                    val value = filteringFetcher.get(env, parent)
                    value
                }
        }
    }

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

    fun buildFetcher() = buildFetcher(DSL.noCondition())

    fun buildFetcher(filteringCondition: Condition): DataFetcher<Iterable<out Record>> {
        return DataFetcher {
            fetchEntity(it, filteringCondition)
                .collect(Collectors.toList())
        }
    }

    fun buildJoinFetcher(
        anotherTableDef: TableImpl<*>,
        condition: Condition,
        keyFields: List<TableField<*, *>>
    ): DataFetcher<Iterable<Record>> {
        return DataFetcher { env ->
            val ctx: DSLContext = getDslContext()
            logger.info("Fetching entity {} using filter {}", tableDef.name, condition);
            try {
                val fields: Set<TableField<*, *>> = buildSelectedColumns(env).plus(keyFields)

                val resultList = ctx.select(fields)
                    .from(tableDef)
                    .join(anotherTableDef).onKey()
                    .where(condition)
                    .fetchStream()
                    .collect(Collectors.toList())
                logger.debug("Fetched {} records of type: {}", resultList.size, tableDef.name);
                resultList
            } finally {
                logger.trace("Closing connection: {}", ctx);
                ctx.close()
            }
        }
    }

    private fun fetchEntity(env: DataFetchingEnvironment, condition: Condition): Stream<T> {
        val ctx: DSLContext = getDslContext()
        logger.info("Fetching entity {} using filter {}", tableDef.name, condition);
        try {
            val fields = buildSelectedColumns(env)
            val filters = buildFilterConditions(env)
            val finalCondition = if (filters.isEmpty()) condition
            else DSL.and(condition, DSL.and(filters))

            return ctx.select(fields)
                .from(tableDef)
                .where(finalCondition)
                .fetchStream()
                .map { it as T? }
        } finally {
            logger.trace("Closing connection: {}", tableDef.name);
            ctx.close()
        }
    }

    private fun buildFilterConditions(env: DataFetchingEnvironment): List<Condition> {
        return env.arguments.entries
            .map {
                val function = filterArgsByName[it.key]
                function?.invoke(it.value) ?: DSL.noCondition()
            }
    }

    private fun getDslContext(): DSLContext = ctxSupplier()

    private fun buildSelectedColumns(env: DataFetchingEnvironment): Set<TableField<*, *>> {
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
        TODO("not jet supported")
    }

    private fun fieldValueOrNull(field: TableField<T, *>, parent: Record?): Any? {
        if (parent == null) {
            return null
        }
        return field.getValue(parent)
    }

    fun buildArguments(): List<GraphQLArgument> {
        return argDefs.toList()
    }

    companion object {
        fun <T : Record> newBuilder(objectType: TableImpl<T>, supplier: () -> DSLContext): EntityBuilder<T> {
            return EntityBuilder(objectType.name, objectType, supplier)
        }

        val logger = LoggerFactory.getLogger(EntityBuilder::class.java)
    }
}
