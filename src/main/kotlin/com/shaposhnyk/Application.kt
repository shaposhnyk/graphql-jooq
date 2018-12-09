package com.shaposhnyk

import graphql.Scalars
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLSchema
import org.jooq.generated.Tables
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.context.annotation.Bean


@SpringBootApplication
class Application {
    @Bean
    fun schema(): GraphQLSchema {
        EntityBuilder.newBuilder(Tables.PERSON)
            .field("ref", Tables.PERSON.PERSONREF)
            .field(Tables.PERSON.CORRELATIONREF)
            .build()

        return GraphQLSchema
            .newSchema()
            .query(
                GraphQLObjectType.newObject()
                    .name("query")
                    .field {
                        it.name("test")
                            .type(Scalars.GraphQLString)
                            .dataFetcher { "response" }
                    }
            )
            .build();
    }
}

fun main(args: Array<String>) {
    SpringApplication.run(Application::class.java, *args)
}
