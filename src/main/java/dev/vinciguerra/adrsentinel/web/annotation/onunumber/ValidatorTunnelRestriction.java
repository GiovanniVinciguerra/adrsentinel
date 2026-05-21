package dev.vinciguerra.adrsentinel.web.annotation.onunumber;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import dev.vinciguerra.adrsentinel.web.annotation.onunumber.validator.TunnelRestrictionValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Annotazione di validazione custom (Constraint) progettata per il controllo perimetrale 
 * (Edge Validation) sul Codice di Restrizione in Galleria (Tunnel Restriction Code) 
 * associato al trasporto di merci pericolose (ADR).
 * Verifica che il dato, qualora fornito, corrisponda esattamente a uno dei codici 
 * riconosciuti a livello normativo (Enum).
 * <p><b>Contesto Architetturale (Optionality & Type-Safety):</b></p>
 * Seguendo il <i>Single Responsibility Principle</i>, questa interfaccia abbraccia la 
 * natura opzionale del parametro. Tollerando l'assenza del dato (valore {@code null}), 
 * riconosce che non tutte le merci ADR sono soggette a restrizioni in galleria. 
 * Tuttavia, se il dato è presente nel payload, funge da scudo (Anti-Corruption Layer) 
 * garantendo una rigorosa mappatura a dizionario (Type-Safety) verso l'enumerazione 
 * {@code TunnelRestriction} (es. C, D, C/E).
 * La logica ispettiva è demandata alla classe {@link TunnelRestrictionValidator}.
 * @author Giovanni Vinciguerra
 * @version 1.0 (ADR Domain Validator)
 * @since 1.0
 * @see TunnelRestrictionValidator
 */
@Documented
@Retention(RUNTIME)
@Target({ FIELD, PARAMETER })
@Constraint(validatedBy = { TunnelRestrictionValidator.class })
public @interface ValidatorTunnelRestriction {
	/**
	 * Definisce il messaggio di errore unificato restituito al client (REST Payload) 
	 * qualora la collezione contenga almeno un elemento vuoto, nullo o non riconosciuto a dizionario.
	 * @return la stringa contenente il messaggio in standard Minimalist REST (es. HTTP 400).
	 */
	String message() default "Malformed payload: the provided tunnel restriction code is unrecognized.";
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
