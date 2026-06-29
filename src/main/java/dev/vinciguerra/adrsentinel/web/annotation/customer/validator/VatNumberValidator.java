package dev.vinciguerra.adrsentinel.web.annotation.customer.validator;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import dev.vinciguerra.adrsentinel.web.annotation.customer.ValidatorVatNumber;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class VatNumberValidator implements ConstraintValidator<ValidatorVatNumber, String> {
	private static final Map<String, Pattern> VAT_PATTERNS = new HashMap<String, Pattern>();
	
	static {
		// Austria (AT): Inizia per 'U' seguita da 8 cifre
        VAT_PATTERNS.put("AT", Pattern.compile("^ATU\\d{8}$"));
        // Belgio (BE): 10 cifre (spesso inizia con 0 o 1)
        VAT_PATTERNS.put("BE", Pattern.compile("^BE[01]\\d{9}$"));
        // Bulgaria (BG): 9 o 10 cifre
        VAT_PATTERNS.put("BG", Pattern.compile("^BG\\d{9,10}$"));
        // Cipro (CY): 8 cifre seguite da 1 lettera
        VAT_PATTERNS.put("CY", Pattern.compile("^CY\\d{8}[A-Z]$"));
        // Croazia (HR): Esattamente 11 cifre (OIB)
        VAT_PATTERNS.put("HR", Pattern.compile("^HR\\d{11}$"));
        // Danimarca (DK): Esattamente 8 cifre (CVR)
        VAT_PATTERNS.put("DK", Pattern.compile("^DK\\d{8}$"));
        // Estonia (EE): Esattamente 9 cifre
        VAT_PATTERNS.put("EE", Pattern.compile("^EE\\d{9}$"));
        // Finlandia (FI): Esattamente 8 cifre
        VAT_PATTERNS.put("FI", Pattern.compile("^FI\\d{8}$"));
        // Francia (FR): 2 caratteri alfanumerici seguiti da 9 cifre (SIREN)
        VAT_PATTERNS.put("FR", Pattern.compile("^FR[A-Z0-9]{2}\\d{9}$"));
        // Germania (DE): Esattamente 9 cifre numeriche (USt-IdNr)
        VAT_PATTERNS.put("DE", Pattern.compile("^DE\\d{9}$"));
        // Grecia (EL): Esattamente 9 cifre (ATTENZIONE: Il prefisso VIES è EL, non GR)
        VAT_PATTERNS.put("EL", Pattern.compile("^EL\\d{9}$"));
        // Irlanda (IE): Formato complesso - 7 cifre + 1/2 lettere, oppure 1 cifra + 1 lettera + 5 cifre + 1 lettera
        // Per robustezza Enterprise usiamo un pattern leggermente tollerante sugli alfanumerici centrali
        VAT_PATTERNS.put("IE", Pattern.compile("^IE([A-Z0-9]{8,9})$"));
        // Italia (IT): Esattamente 11 cifre numeriche
        VAT_PATTERNS.put("IT", Pattern.compile("^IT\\d{11}$"));
        // Lettonia (LV): Esattamente 11 cifre
        VAT_PATTERNS.put("LV", Pattern.compile("^LV\\d{11}$"));
        // Lituania (LT): 9 oppure 12 cifre
        VAT_PATTERNS.put("LT", Pattern.compile("^LT(\\d{9}|\\d{12})$"));
        // Lussemburgo (LU): Esattamente 8 cifre
        VAT_PATTERNS.put("LU", Pattern.compile("^LU\\d{8}$"));
        // Malta (MT): Esattamente 8 cifre
        VAT_PATTERNS.put("MT", Pattern.compile("^MT\\d{8}$"));
        // Olanda (NL): 9 cifre, seguite da 'B' e 2 cifre
        VAT_PATTERNS.put("NL", Pattern.compile("^NL\\d{9}B\\d{2}$"));
        // Polonia (PL): Esattamente 10 cifre
        VAT_PATTERNS.put("PL", Pattern.compile("^PL\\d{10}$"));
        // Portogallo (PT): Esattamente 9 cifre (NIF)
        VAT_PATTERNS.put("PT", Pattern.compile("^PT\\d{9}$"));
        // Repubblica Ceca (CZ): Da 8 a 10 cifre
        VAT_PATTERNS.put("CZ", Pattern.compile("^CZ\\d{8,10}$"));
        // Romania (RO): Da 2 a 10 cifre (CUI)
        VAT_PATTERNS.put("RO", Pattern.compile("^RO\\d{2,10}$"));
        // Slovacchia (SK): Esattamente 10 cifre
        VAT_PATTERNS.put("SK", Pattern.compile("^SK\\d{10}$"));
        // Slovenia (SI): Esattamente 8 cifre
        VAT_PATTERNS.put("SI", Pattern.compile("^SI\\d{8}$"));
        // Spagna (ES): 9 caratteri alfanumerici (può iniziare/finire con lettera)
        VAT_PATTERNS.put("ES", Pattern.compile("^ES[A-Z0-9]\\d{7}[A-Z0-9]$"));
        // Svezia (SE): Esattamente 12 cifre
        VAT_PATTERNS.put("SE", Pattern.compile("^SE\\d{12}$"));
        // Ungheria (HU): Esattamente 8 cifre
        VAT_PATTERNS.put("HU", Pattern.compile("^HU\\d{8}$"));
        // PROTOCOLLO IRLANDA DEL NORD (Post-Brexit)
        // Irlanda del Nord (XI): 9 o 12 cifre. Fondamentale per la logistica comunitaria post-Brexit
        VAT_PATTERNS.put("XI", Pattern.compile("^XI(\\d{9}|\\d{12})$"));
	}
	
	@Override
	public boolean isValid(String value, ConstraintValidatorContext context) {
		// 1. Fail-Fast sui nulli o vuoti
		if(value == null || value.isBlank())
			return false;
		// 2. Normalizzazione: Rimuove spazi, trattini, punti e forza il maiuscolo.
        // Questo permette all'utente di scrivere "IT 01234567890" o "FR-XX-12345" e passare comunque la validazione.
		String normalizedVat = value.replaceAll("[\\s,\\.\\-/_]+", "").toUpperCase();
		// 3. Controllo lunghezza fisica estrema (Minimo 4 per vecchi formati UK/EU, massimo 15)
		if(normalizedVat.length() < 4 || normalizedVat.length() > 15)
			return false;
		// 4. Estrazione del Country Code (prime due lettere)
		String countryCode = normalizedVat.substring(0, 2);
		// Se le prime due non sono lettere, non è un VAT europeo valido per le operazioni transfrontaliere
		if(!countryCode.matches("^[A-Z]{2}$"))
			return false;
		Pattern pattern = VAT_PATTERNS.get(countryCode);
		if(pattern != null)
			return pattern.matcher(normalizedVat).matches();
		else
			// Se è un paese che non è ancora mappato, applica una validazione di fallback
            // (Accetta 2 lettere seguite da 2 a 12 caratteri alfanumerici, standard VIES generico)
			return normalizedVat.matches("^[A-Z]{2}[A-Z0-9]{2,12}$");
	}
}
