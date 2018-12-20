package com.shaposhnyk

import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLSchema
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.SQLDialect
import org.jooq.generated.Tables
import org.jooq.impl.DSL
import org.jooq.impl.TableImpl
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.context.annotation.Bean
import javax.sql.DataSource


@SpringBootApplication
class Application {
    @Bean
    @Autowired
    fun schema(dataSource: DataSource): GraphQLSchema {
        // base props defs
        val ident = EntityBuilder.newBuilder(Tables.IDENTIFICATION) { dsl(dataSource) }
            .field("firstName", Tables.IDENTIFICATION.FIRST_NAME)
            .field("lastName", Tables.IDENTIFICATION.LAST_NAME) { it?.toUpperCase() }
            .fieldOf { fBuilder ->
                fBuilder.withName("displayName")
                    .withSourceColumns(Tables.IDENTIFICATION.FIRST_NAME, Tables.IDENTIFICATION.LAST_NAME)
                    .extractFrom { displayName(it) }
            }
            .field("yearOfBirth", Tables.IDENTIFICATION.YEAR_OF_BIRTH)
            .field(Tables.IDENTIFICATION.NATURE)

        val nationality = EntityBuilder.newBuilder(Tables.NATIONALITY) { dsl(dataSource) }
            .field("countryRef", Tables.NATIONALITY.COUNTRYCODE)

        val relations = newBuilder(Tables.RELATIONS, dataSource)
            .fieldAndFilter("kind", Tables.RELATIONS.TYPE)

        val people = newBuilder(Tables.PERSON, dataSource)
            .fieldAndFilter("ref", Tables.PERSON.PERSONREF)
            .field(Tables.PERSON.CORRELATIONREF)
            .filterNotEq("refNot", Tables.PERSON.PERSONREF)
            .filterContains("refLike", Tables.PERSON.PERSONREF)

        // relations

        relations
            .fieldOf { fBuilder ->
                fBuilder.withName("person")
                    .withType(people.buildObjectTypeRef())
                    .withSourceColumn(Tables.RELATIONS.CHILDREF)
                    .extractFromContext { env, r ->
                        val childRef: String = Tables.RELATIONS.CHILDREF.getValue(r)
                        val childList = people.buildFetcher(Tables.PERSON.PERSONREF.eq(childRef))
                            .get(env)
                        childList.first()
                    }
            }

        people
            .relOneToMany(nationality)
            .relOneToManyAtOnce(ident)
            .relOneToMany("inRelationWith", relations, Tables.RELATIONS.PARENTREF)

        return GraphQLSchema
            .newSchema()
            .additionalType(ident.buildObjectType().build())
            .additionalType(nationality.buildObjectType().build())
            .additionalType(relations.buildObjectType().build())
            .additionalType(people.buildObjectType().build())
            .query(
                GraphQLObjectType.newObject()
                    .name("queries")
                    .field(people.defaultList("findPeople"))
            )
            .build();
    }

    private fun <T : Record> newBuilder(table: TableImpl<T>, dataSource: DataSource): EntityBuilder<T> {
        return EntityBuilder.newBuilder(table) { dsl(dataSource) }
    }

    private fun displayName(it: Record?): String {
        return "M. " + Tables.IDENTIFICATION.FIRST_NAME.get(it) +
                " " + Tables.IDENTIFICATION.LAST_NAME.get(it)?.toUpperCase()
    }

    private fun dsl(dataSource: DataSource): DSLContext {
        return DSL.using(dataSource, SQLDialect.H2)
    }
}

fun main(args: Array<String>) {
    SpringApplication.run(Application::class.java, *args)
}
