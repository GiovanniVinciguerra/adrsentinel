package dev.vinciguerra.adrsentinel.web.annotation.vehicle.validator;

import dev.vinciguerra.adrsentinel.web.annotation.vehicle.ValidatorAxlesNumber;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Implementazione concreta del vincolo di validazione perimetrale e meccanico {@link ValidatorAxlesNumber}.
 * Agisce come scudo architetturale (Edge Validation) per il numero di assi (Axle Count) di veicoli 
 * commerciali o convogli nel dominio della logistica pesante e dei trasporti ADR.
 * <p><b>Design Architetturale (Strict Validation & Domain-Driven Boundaries):</b></p>
 * Questa classe traduce la meccanica dei veicoli su gomma in una regola software inossidabile. 
 * Lavorando su grandezze intere discrete, garantisce la presenza del dato e impone un rigoroso 
 * Boundary Check (Controllo di Confine). L'intervallo [2 - 8] assi modella l'effettiva realtà 
 * ingegneristica e normativa: partendo da un minimo assoluto di 2 assi (necessari per l'equilibrio 
 * e la trazione di qualsiasi mezzo su strada), fino a un massimo operativo di 8 assi, tipico 
 * per i convogli speciali o i trasporti eccezionali.
 * @author Giovanni Vinciguerra
 * @version 1.0
 * @since 3.0
 * @see ValidatorAxlesNumber
 */
public class AxlesNumberValidator implements ConstraintValidator<ValidatorAxlesNumber, Integer> {
	/**
	 * Esegue l'ispezione profonda del valore numerico intero per certificarne l'obbligatorietà 
	 * e la totale aderenza alle leggi della fisica veicolare.
	 * <p><b>Flusso di Esecuzione (Fail-Fast & Arithmetic Safety):</b></p>
	 * <ol>
	 * <li><b>Guard Clause (Strict Presence Check):</b> Intercetta e respinge istantaneamente 
	 * i valori {@code null}. L'obbligatorietà di questo parametro previene le fatali 
	 * {@code NullPointerException} durante la fase di unboxing a valle.</li>
	 * <li><b>Boundary Check (Range [2, 8]):</b> Impone che il numero intero rientri esattamente 
	 * nel range consentito. Questo passaggio è cruciale per la <i>Arithmetic Safety</i> del sistema: 
	 * scartando lo zero e l'uno, sterilizza istantaneamente il payload da errori di input che, 
	 * se usati come divisori nelle formule di "carico per asse", innescherebbero letali 
	 * {@code ArithmeticException} (Division by Zero) nei motori di calcolo logistico.</li>
	 * </ol>
	 * @param value Il valore numerico intero (numero di assi) estratto dal Request Payload.
	 * @param context Il contesto di validazione iniettato dal framework (Spring/Hibernate Validator).
	 * @return {@code true} esclusivamente se il valore è presente e matematicamente compreso 
	 * nel range chiuso [2, 8]; {@code false} in caso di assenza del dato o se il veicolo 
	 * descritto risulta fisicamente o legalmente impossibile.
	 */
	@Override
	public boolean isValid(Integer value, ConstraintValidatorContext context) {
		if(value == null)
			return false;
		return value >= 2 && value <= 8;
	}
}
