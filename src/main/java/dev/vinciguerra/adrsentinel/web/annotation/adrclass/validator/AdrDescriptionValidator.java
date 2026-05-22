package dev.vinciguerra.adrsentinel.web.annotation.adrclass.validator;

import java.util.regex.Pattern;
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
	 * Whitelist di sicurezza per il campo description di AdrClass:
	 * <ul>
	 * <li><b>\p{L}:</b> Qualsiasi lettera (inclusi accenti) </li>
	 * <li><b>0-9:</b> Numeri </li>
	 * <li><b>\s:</b> Spazi, tab, ritorni a capo </li>
	 * <li><b>\-',.():</b> Trattini, apostrofi, virgole, punti e parentesi tonde </li>
	 * <li><b>+:</b> Almeno un carattere di questi </li>
	 * </ul>
	 * <p>
	 * <b>N.B</b> la stringa è valida esclusivamente se possiede solo e solo questi caratteri consentiti.
	 * </p>
	 */
	private final static Pattern WHITELIST_DESCRITION_PATTERN = Pattern.compile("^[\\p{L}0-9\\s\\-',.()]+$");
	
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
	 * <li><b>Whitelisting</b>: Intercetta e respinge qualunque stringa che non corrisponde al pattern del campo 
	 * description di una classe ADR.
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
		if(value.length() < 3 || value.length() > 255)
			return false;
		return WHITELIST_DESCRITION_PATTERN.matcher(value).matches();
	}
}
