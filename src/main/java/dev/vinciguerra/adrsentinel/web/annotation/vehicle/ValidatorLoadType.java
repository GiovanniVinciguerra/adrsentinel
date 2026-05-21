package dev.vinciguerra.adrsentinel.web.annotation.vehicle;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import dev.vinciguerra.adrsentinel.web.annotation.vehicle.validator.LoadTypeValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Annotazione di validazione custom (Constraint) progettata per il controllo perimetrale 
 * (Edge Validation) sulla Tassonomia di Dominio relativa al Tipo di Carico (Load Type) 
 * nel contesto logistico e dei trasporti di merci (es. normativa ADR).
 * <p><b>Contesto Architetturale (String-to-Enum Safe Binding):</b></p>
 * Questa interfaccia riutilizza il consolidato pattern di sicurezza architetturale per le API REST. 
 * Per prevenire crash di deserializzazione (es. {@code HttpMessageNotReadableException}) causati da 
 * mismatch diretti tra JSON ed Enum, l'annotazione accetta una {@code String} cruda dal client. 
 * Il validatore associato tenta in modo sicuro il binding con l'enumerazione di sistema {@code LoadType}. 
 * Trattandosi del dominio ADR, dove la classificazione della materia (es. Solido, Liquido, Gas) 
 * altera drasticamente le regole di instradamento e incompatibilità di carico, questa validazione 
 * rigorosa a dizionario chiuso garantisce la massima integrità dei dati prima che raggiungano 
 * i motori di calcolo del Service Layer.
 * La logica ispettiva e di mapping è demandata alla classe {@link LoadTypeValidator}.
 * @author Giovanni Vinciguerra
 * @version 1.0 (ADR Domain Validator)
 * @since 1.0
 * @see LoadTypeValidator
 */
@Documented
@Retention(RUNTIME)
@Target({ FIELD, PARAMETER })
@Constraint(validatedBy = { LoadTypeValidator.class })
public @interface ValidatorLoadType {
	/**
	 * Definisce il messaggio di errore unificato restituito al client (REST Payload) 
	 * qualora la collezione contenga almeno un elemento vuoto, nullo o non riconosciuto a dizionario.
	 * @return la stringa contenente il messaggio in standard Minimalist REST (es. HTTP 400).
	 */
	String message() default "Malformed payload: the required load type is missing or not recognized.";
	/**
	 * Partiziona l'esecuzione del vincolo associandolo a specifici gruppi di validazione 
	 * (Validation Groups).
	 * @return l'array delle classi (gruppi) a cui appartiene questo vincolo (di default vuoto).
	 */
	Class<?>[] groups() default {};
	/**
	 * Consente all'architettura di sistema di allegare metadati informativi (Payload) 
	 * alla violazione del vincolo, utili per tracciare livelli di severità dell'errore (es. WARNING, FATAL).
	 * @return l'array delle classi di payload estese (di default vuoto).
	 */
	Class<? extends Payload>[] payload() default {};
}
