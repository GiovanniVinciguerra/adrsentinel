package dev.vinciguerra.adrsentinel.web.annotation.adrclass.validator;

import dev.vinciguerra.adrsentinel.web.annotation.adrclass.ValidatorAdrDescription;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Implementazione concreta del vincolo di validazione composito {@link ValidatorAdrDescription}.
 * Funge da barriera perimetrale (Edge Validation) per garantire che le descrizioni testuali 
 * delle classi ADR siano obbligatoriamente presenti e rispettino i vincoli dimensionali.
 * <p><b>Design Architetturale (Strict & Composite Validation):</b></p>
 * Questa classe adotta un approccio restrittivo a doppia fase. Fonde la logica di 
 * obbligatorietà (assorbendo il comportamento nativo di {@code @NotBlank}) con la validazione 
 * dei limiti di archiviazione (assorbendo {@code @Size}). Questo design pattern riduce 
 * il carico computazionale del framework e mantiene i Data Transfer Object estremamente puliti.
 * @author Giovanni Vinciguerra
 * @version 1.0
 * @since 3.0
 * @see ValidatorAdrDescription
 */
public class AdrDescriptionValidator implements ConstraintValidator<ValidatorAdrDescription, String> {
	/**
	 * Esegue l'ispezione della stringa in ingresso per certificarne la validità strutturale e dimensionale.
	 * <p><b>Flusso di Esecuzione (Fail-Fast):</b></p>
	 * <ol>
	 * <li><b>Guard Clause (Controllo di Presenza):</b> Intercetta e respinge immediatamente valori 
	 * {@code null}, stringhe vuote o composte esclusivamente da spazi bianchi (tramite l'uso di {@code isBlank()}).</li>
	 * <li><b>Boundary Check (Limiti Dimensionali):</b> Verifica che la lunghezza della stringa 
	 * sia matematicamente compresa nel range prestabilito [3, 255]. Questo garantisce la 
	 * perfetta compatibilità e sicurezza nell'inserimento all'interno della relativa colonna 
	 * (es. VARCHAR) sul database.</li>
	 * </ol>
	 * @param value La stringa descrittiva estratta dal Request Payload.
	 * @param context Il contesto di validazione fornito dal framework (Spring/Hibernate Validator).
	 * @return {@code true} esclusivamente se la stringa è presente, non vuota e rientra nei limiti di lunghezza; 
	 * {@code false} in caso di assenza o violazione dei limiti.
	 */
	@Override
	public boolean isValid(String value, ConstraintValidatorContext context) {
		if(value == null || value.isBlank())
			return false;
		return value.length() >= 3 && value.length() <= 255;
	}
}
