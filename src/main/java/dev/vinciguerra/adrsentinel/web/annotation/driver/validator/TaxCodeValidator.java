package dev.vinciguerra.adrsentinel.web.annotation.driver.validator;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import dev.vinciguerra.adrsentinel.web.annotation.driver.ValidatorTaxCode;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Implementazione concreta della logica di validazione per l'annotazione custom {@link ValidatorTaxCode}.
 * <p>Questa classe funge da motore di validazione per i Numeri di Identificazione Fiscale 
 * (TIN - Tax Identification Number) adottati in Europa (es. Codice Fiscale italiano, NIF spagnolo, 
 * Steuer-IdNr tedesco, National Insurance Number britannico).</p>
 * <p><b>Strategia di Validazione:</b></p>
 * <p>Il validatore adotta un approccio "agnostico" per nazionalità. La stringa in input viene considerata 
 * valida se, dopo un processo di igienizzazione, risulta conforme ad <b>almeno uno</b> dei pattern Regex 
 * definiti nel dizionario in memoria.</p>
 * @author Giovanni Vinciguerra
 * @version 1.0
 * @since 3.0
 * @see ValidatorTaxCode
 */
public class TaxCodeValidator implements ConstraintValidator<ValidatorTaxCode, String> {
	/**
     * Dizionario in memoria precompilato contenente le espressioni regolari (Regex) associate 
     * ai formati dei codici fiscali (TIN) nazionali.
     * <p>L'uso di {@link Pattern#compile(String)} all'interno del blocco statico garantisce 
     * prestazioni ottimali, compilando le regex una sola volta al caricamento della classe 
     * nella JVM.</p>
     */
	private static final Map<String, Pattern> TIN_PATTERNS = new HashMap<String, Pattern>();
	
	static {
        // --- PRINCIPALI STATI MEMBRI UE ED EXTRA-UE ---
        // Italia (IT): Codice Fiscale - 16 caratteri alfanumerici (struttura rigida)
        TIN_PATTERNS.put("IT", Pattern.compile("^[A-Z]{6}\\d{2}[A-Z]\\d{2}[A-Z]\\d{3}[A-Z]$"));
        // Spagna (ES): NIF / NIE - 9 caratteri (inizia con lettera o numero, finisce con lettera)
        TIN_PATTERNS.put("ES", Pattern.compile("^[XYZ\\d]\\d{7}[A-Z]$"));
        // Germania (DE): Steuer-Identifikationsnummer - 11 cifre
        TIN_PATTERNS.put("DE", Pattern.compile("^\\d{11}$"));
        // Francia (FR): Numéro fiscal (SPI) - 13 cifre
        TIN_PATTERNS.put("FR", Pattern.compile("^\\d{13}$"));
        // Regno Unito (GB): National Insurance Number (NINO) - 9 caratteri (2 lettere, 6 cifre, 1 lettera)
        // Sanitizzato senza spazi
        TIN_PATTERNS.put("GB", Pattern.compile("^[A-Z]{2}\\d{6}[A-D]$"));
        // Polonia (PL): PESEL (11 cifre) o NIP (10 cifre)
        TIN_PATTERNS.put("PL", Pattern.compile("^\\d{10,11}$"));
        // Paesi Bassi (NL): BSN (Burgerservicenummer) - 9 cifre
        TIN_PATTERNS.put("NL", Pattern.compile("^\\d{9}$"));
        // Belgio (BE): NISS (Numéro de Registre National) - 11 cifre
        TIN_PATTERNS.put("BE", Pattern.compile("^\\d{11}$"));
        // Austria (AT): Abgabenkontonummer o TIN generico - 9 cifre
        TIN_PATTERNS.put("AT", Pattern.compile("^\\d{9}$"));
        // Svezia (SE): Personnummer - 10 o 12 cifre (sanitizzato, senza il trattino o il segno '+')
        TIN_PATTERNS.put("SE", Pattern.compile("^\\d{10}(\\d{2})?$"));
        // Svizzera (CH): AHV-Nummer / numero AVS - 13 cifre (spesso inizia con 756)
        TIN_PATTERNS.put("CH", Pattern.compile("^\\d{13}$"));
        // Portogallo (PT): NIF (Número de Identificação Fiscal) - 9 cifre
        TIN_PATTERNS.put("PT", Pattern.compile("^\\d{9}$"));
        // Romania (RO): CNP (Cod Numeric Personal) - 13 cifre
        TIN_PATTERNS.put("RO", Pattern.compile("^\\d{13}$"));
        // Irlanda (IE): PPS No - 7 cifre seguite da 1 o 2 lettere
        TIN_PATTERNS.put("IE", Pattern.compile("^\\d{7}[A-Z]{1,2}$"));
    }
	
	/**
     * Valida il codice fiscale (TIN) fornito in input applicando una catena di controlli sequenziali.
     * <p><b>Fasi dell'algoritmo di validazione:</b></p>
     * <ol>
     * <li><b>Pre-controllo:</b> Verifica immediata della presenza del dato (fallisce se nullo o stringa vuota).</li>
     * <li><b>Igienizzazione (Sanitization):</b> Rimozione dei caratteri separatori comunemente inseriti dagli utenti 
     * (spazi, virgole, punti, trattini, slash, underscore) tramite la Regex <code>[\s,\.\-/_]+</code>.</li>
     * <li><b>Normalizzazione:</b> Conversione in lettere maiuscole (Uppercase).</li>
     * <li><b>Boundary Check:</b> Scarto immediato di stringhe con lunghezza fuori dal range consentito dai 
     * formati europei (minimo 8, massimo 16 caratteri). Ottimizza le risorse evitando elaborazioni Regex inutili.</li>
     * <li><b>Pattern Matching:</b> Valutazione dello stream di pattern. Restituisce <code>true</code> 
     * alla prima occorrenza valida trovata (metodo <code>anyMatch</code>).</li>
     * </ol>
     * @param value La stringa inviata dal client rappresentante il TIN da validare.
     * @param context Il contesto di validazione che fornisce le API per la gestione degli errori.
     * @return <code>true</code> se la stringa (dopo la sanitizzazione) rispetta il formato di almeno 
     * uno dei Paesi censiti, <code>false</code> altrimenti.
     */
	@Override
	public boolean isValid(String value, ConstraintValidatorContext context) {
		if(value == null || value.isBlank())
			return false;
		String manipulatedValue = value.replaceAll("[\\s,\\.\\-/_]+", "").toUpperCase();
		if(manipulatedValue.length() < 8 || manipulatedValue.length() > 16)
			return false;
		return TIN_PATTERNS.values().stream()
			.anyMatch(pattern -> pattern.matcher(manipulatedValue).matches());
	}
}
