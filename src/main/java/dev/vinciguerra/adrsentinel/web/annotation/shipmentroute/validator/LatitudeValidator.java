package dev.vinciguerra.adrsentinel.web.annotation.shipmentroute.validator;

import dev.vinciguerra.adrsentinel.web.annotation.shipmentroute.ValidatorLatitude;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Implementazione concreta del motore di validazione per il vincolo geografico {@link ValidatorLatitude}.
 * <p>
 * Questa classe viene istanziata e invocata automaticamente dal framework di validazione 
 * (es. Hibernate Validator) ogni volta che un campo annotato con {@code @ValidatorLatitude} 
 * deve essere verificato. Agisce come barriera di sicurezza (Defensive Programming) per 
 * impedire che dati spaziali corrotti o fisicamente impossibili raggiungano il livello di persistenza 
 * o il motore cartografico esterno (es. OpenRouteService).
 * </p>
 * <p>
 * <b>Responsabilità Architetturali:</b>
 * <ul>
 * <li><b>Type-Safety:</b> Intercetta e blocca le anomalie dello standard IEEE 754 per i numeri in virgola mobile (Not-a-Number, Infiniti).</li>
 * <li><b>Domain-Safety:</b> Garantisce il rispetto dei confini fisici del globo terrestre (da Polo Sud a Polo Nord).</li>
 * <li><b>Separation of Concerns:</b> Delega la verifica dell'obbligatorietà del dato (Nullability) alle annotazioni standard (es. {@code @NotNull}).</li>
 * </ul>
 * </p>
 * @author Giovanni Vinciguerra
 * @version 1.0
 * @since 3.0
 * @see ValidatorLatitude
 */
public class LatitudeValidator implements ConstraintValidator<ValidatorLatitude, Double> {
	/**
	 * Valuta se il valore della latitudine fornito rispetta i vincoli fisici e matematici del dominio.
	 * <p>
	 * <b>Flusso di Validazione (Execution Flow):</b>
	 * <ol>
	 * <li><b>Tolleranza all'Assenza (JSR-380):</b> Se il valore è {@code null}, il metodo restituisce {@code true}. 
	 * Questa è una direttiva architetturale standard per i validatori custom: l'obbligatorietà del dato 
	 * deve essere controllata ortogonalmente tramite {@link jakarta.validation.constraints.NotNull}. 
	 * Restituire {@code false} qui causerebbe la sovrapposizione di due messaggi di errore identici nel payload REST.</li>
	 * <li><b>Integrità IEEE 754:</b> Verifica che il valore non sia il risultato di una divisione per zero 
	 * ({@code isInfinite()}) o di un'operazione matematica indefinita ({@code isNaN()}).</li>
	 * <li><b>Integrità Geografica:</b> Verifica che l'angolo sia rigorosamente contenuto nell'intervallo chiuso 
	 * [-90.0, 90.0] gradi.</li>
	 * </ol>
	 * </p>
	 * @param value L'istanza di {@link Double} rappresentante la latitudine da validare (può essere null).
	 * @param context Il contesto di validazione fornito dal framework, utilizzabile per sovrascrivere 
	 * dinamicamente il messaggio di errore di default (non utilizzato in questa implementazione).
	 * @return {@code true} se il valore è nullo o geograficamente valido; {@code false} se il valore è corrotto o fuori scala.
	 */
	@Override
	public boolean isValid(Double value, ConstraintValidatorContext context) {
		if(value == null)
			return false;
		if(value.isInfinite() || value.isNaN())
			return false;
		return value >= -90.0 && value <= 90.0;
	}
}
