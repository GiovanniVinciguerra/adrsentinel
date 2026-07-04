package dev.vinciguerra.adrsentinel.web.annotation.customer.validator;

import java.util.regex.Pattern;
import dev.vinciguerra.adrsentinel.web.annotation.customer.ValidatorCompanyName;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Validatore JSR-380 (Hibernate Validator) customizzato per la sanificazione e la validazione 
 * formale della Ragione Sociale (Company Name) degli attori logistici.
 * <p>
 * <b>Contesto Architetturale (Anti-Corruption Layer & Security):</b><br>
 * L'input testuale libero rappresenta una delle principali superfici di attacco per vulnerabilità 
 * come XSS (Cross-Site Scripting) e SQL Injection. Questa classe agisce come un severo filtro 
 * di sicurezza al confine dell'applicazione. Invece di tentare di indovinare ed escludere i 
 * caratteri pericolosi (Blacklist pattern), implementa un rigoroso <b>Whitelist pattern</b>: 
 * definisce in modo esplicito e chiuso l'esatto set di caratteri ammessi per una ragione sociale, 
 * rigettando automaticamente qualsiasi input non conforme.
 * </p>
 * <p>
 * <b>Internazionalizzazione (Supporto Europeo):</b><br>
 * Essendo progettato per operare su anagrafiche transfrontaliere all'interno del sistema logistico 
 * di AdrSentinel, il validatore garantisce il pieno supporto agli alfabeti internazionali 
 * (es. vocali accentate italiane, umlaut tedeschi, cediglie francesi) tramite l'engine regex nativo di Java.
 * </p>
 * @author Giovanni Vinciguerra
 * @version 1.0
 * @since 3.0
 * @see ValidatorCompanyName
 */
public class CompanyNameValidator implements ConstraintValidator<ValidatorCompanyName, String> {
	/**
	 * Espressione regolare compilata staticamente per massimizzare le performance a runtime.
	 * <p>
	 * <b>Anatomia della Whitelist:</b>
	 * <ul>
	 * <li>{@code ^} e {@code $}: Ancore di inizio e fine stringa, impediscono l'iniezione di 
	 * codice malevolo ai margini del payload.</li>
	 * <li>{@code \p{L}}: Token Unicode universale. Rappresenta la pietra angolare dell'internazionalizzazione, 
	 * ammettendo qualsiasi lettera di qualsiasi lingua, superando i limiti del classico [a-zA-Z].</li>
	 * <li>{@code \d} e {@code \s}: Ammettono rispettivamente cifre numeriche (es. "3M") e spazi bianchi.</li>
	 * <li>{@code \-&.,'()/\"+}: Set circoscritto di punteggiatura "Corporate" consentita (trattini, 
	 * e-commerciale, punti, virgole, apostrofi, parentesi, slash, virgolette e il segno più).</li>
	 * </ul>
	 * </p>
	 */
	private final static Pattern COMPANY_NAME_PATTERN = Pattern.compile("^[\\p{L}\\d\\s\\-&.,'()/\"+]+$");
	
	/**
	 * Esegue l'ispezione della stringa in ingresso per validarne la conformità strutturale 
	 * e tipografica.
	 * @param value La stringa grezza rappresentante la Ragione Sociale, ricevuta dal payload HTTP.
	 * @param context Il contesto di validazione JSR-380, utilizzabile per l'interpolazione 
	 * di messaggi di errore customizzati.
	 * @return {@code true} se la stringa supera in sequenza i controlli di null-safety, 
	 * dimensionali e tipografici. {@code false} se risulta nulla, vuota, di lunghezza incoerente 
	 * o se contiene caratteri non esplicitamente autorizzati dalla whitelist.
	 */
	@Override
	public boolean isValid(String value, ConstraintValidatorContext context) {
		if(value == null || value.isBlank())
			return false;
		if(value.trim().length() < 2 || value.length() > 255)
			return false;
		return COMPANY_NAME_PATTERN.matcher(value).matches();
	}
}
