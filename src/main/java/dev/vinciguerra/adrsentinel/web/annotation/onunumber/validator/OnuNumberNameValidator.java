package dev.vinciguerra.adrsentinel.web.annotation.onunumber.validator;

import java.util.regex.Pattern;
import dev.vinciguerra.adrsentinel.web.annotation.onunumber.ValidatorOnuNumberName;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Implementazione concreta del vincolo di validazione composito {@link ValidatorOnuNumberName}.
 * Funge da barriera perimetrale (Edge Validation) per garantire che la denominazione 
 * tecnica associata al Numero ONU (Proper Shipping Name) sia obbligatoriamente presente 
 * e rispetti i vincoli dimensionali di archiviazione.
 * <p><b>Design Architetturale (Strict & Composite Validation):</b></p>
 * Questa classe adotta un approccio restrittivo e composito. Sostituisce l'uso 
 * combinato di annotazioni infrastrutturali standard (come {@code @NotBlank} e {@code @Size}), 
 * fondendo il controllo di obbligatorietà logica del dato con la validazione dei limiti 
 * fisici (Length). Questo design pattern alleggerisce i Data Transfer Object e 
 * centralizza la semantica di Dominio.
 * @author Giovanni Vinciguerra
 * @version 1.0
 * @since 3.0
 * @see ValidatorOnuNumberName
 */
public class OnuNumberNameValidator implements ConstraintValidator<ValidatorOnuNumberName, String> {
	/**
	 * Whitelist di sicurezza per il campo name di OnuNumber:
	 * <ul>
	 * <li><b>\p{L}:</b> Qualsiasi lettera (inclusi accenti) </li>
	 * <li><b>0-9:</b> Numeri (fondamentali per l'identificazione chimica, es. 1,2-dicloroetano) </li>
	 * <li><b>\s:</b> Spazi, tab, ritorni a capo </li>
	 * <li><b>\-',.()°:</b> Trattini, apostrofi, virgole, punti (es. per N.A.S.), parentesi e gradi. </li>
	 * </ul>
	 * <p>
	 * <b>N.B</b> la stringa è valida esclusivamente se possiede solo e solo questi caratteri consentiti.
	 * </p>
	 */
	private final static Pattern WHITELIST_NAME_PATTERN = Pattern.compile("^[\\p{L}0-9\\s\\-',.()°]+$");
	
	/**
	 * Esegue l'ispezione della stringa in ingresso per certificarne la validità strutturale e dimensionale.
	 * <p><b>Flusso di Esecuzione (Fail-Fast):</b></p>
	 * <ol>
	 * <li><b>Guard Clause (Strict Presence Check):</b> Intercetta e respinge immediatamente 
	 * valori {@code null}, stringhe vuote o composte esclusivamente da spazi bianchi 
	 * (tramite l'uso ottimizzato di {@code isBlank()}). Questo passaggio impone l'assoluta 
	 * obbligatorietà del campo, trattando l'assenza come una violazione del dominio.</li>
	 * <li><b>Boundary Check (Limiti Dimensionali):</b> Verifica che la lunghezza della 
	 * stringa fornita sia matematicamente compresa nel range prestabilito [3, 255]. 
	 * Questo step previene eccezioni SQL o errori di troncamento (Data Truncation) 
	 * durante la persistenza (es. su colonna VARCHAR).</li>
	 * <li><b>Whitelisting</b>: Intercetta e respinge qualunque stringa che non corrisponde al pattern del campo 
	 * name di una classe onu.
	 * </ol>
	 * @param value La denominazione tecnica ONU estratta dal Request Payload.
	 * @param context Il contesto di validazione iniettato dal framework (Spring/Hibernate Validator).
	 * @return {@code true} esclusivamente se la stringa è presente, non vuota e rientra 
	 * nei limiti di lunghezza prestabiliti; {@code false} in caso di assenza, stringa 
	 * vuota o violazione dei limiti dimensionali.
	 */
	@Override
	public boolean isValid(String value, ConstraintValidatorContext context) {
		if(value == null || value.isBlank())
			return false;
		if(value.length() < 3 || value.length() > 255)
			return false;
		return WHITELIST_NAME_PATTERN.matcher(value).matches();
	}
}
