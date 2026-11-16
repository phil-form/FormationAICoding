package be.bstorm.formation.framework.entity;

import org.hibernate.annotations.IdGeneratorType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Identifiant UUID v7 généré à la persistance, sauf s'il a été attribué avant ({@link UuidV7Generator}). */
@IdGeneratorType(UuidV7Generator.class)
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD})
public @interface UuidV7Id {
}
