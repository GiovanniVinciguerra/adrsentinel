package dev.vinciguerra.adrsentinel.web.annotation.vehicle;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import dev.vinciguerra.adrsentinel.web.annotation.vehicle.validator.WheelbaseValidtor;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Annotazione di validazione custom (Constraint) progettata per il controllo perimetrale 
 * (Edge Validation) sul passo del veicolo (Wheelbase), inteso come distanza longitudinale 
 * tra l'asse anteriore e l'asse posteriore, nel dominio dei trasporti commerciali e ADR.
 * Verifica simultaneamente la presenza obbligatoria del dato, la sua finitezza matematica 
 * e il rispetto dei limiti strutturali dell'ingegneria veicolare.
 * <p><b>Contesto Architetturale (Strict Validation & Domain-Driven Design):</b></p>
 * Questa interfaccia è il fulcro per la validazione della dinamica del veicolo. Assorbendo 
 * l'obbligatorietà logica e applicando un rigoroso Boundary Check, assicura che il Service 
 * Layer operi esclusivamente su autotelai fisicamente realizzabili. Il range implicito 
 * [1.9 - 7.0] (metri) copre lo spettro operativo reale: partendo dalla base di 1.9 metri 
 * tipica dei veicoli ultracompatti, fino al limite estremo di 7.0 metri, caratteristico degli 
 * autotelai rigidi per mezzi pesanti a tre o quattro assi. Questo sbarramento difensivo previene 
 * alla radice la compromissione dei motori di calcolo logistico che dipendono dal passo 
 * (es. algoritmi per il raggio di sterzata e simulazione della ripartizione del carico sugli assi).
 * La logica ispettiva è demandata alla classe {@link WheelbaseValidator}.
 * @author Giovanni Vinciguerra
 * @version 1.0 (ADR Domain Validator)
 * @since 1.0
 * @see WheelbaseValidator
 */
@Documented
@Retention(RUNTIME)
@Target({ FIELD, PARAMETER })
@Constraint(validatedBy = { WheelbaseValidtor.class })
public @interface ValidatorWheelbase {
	/**
	 * Definisce il messaggio di errore unificato restituito al client (REST Payload) 
	 * qualora la collezione contenga almeno un elemento vuoto, nullo o non riconosciuto a dizionario.
	 * @return la stringa contenente il messaggio in standard Minimalist REST (es. HTTP 400).
	 */
	String message() default "Malformed payload: the required numeric value is missing, invalid, or out of bounds (expected a finite float between 1.9 and 7.0).";
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
