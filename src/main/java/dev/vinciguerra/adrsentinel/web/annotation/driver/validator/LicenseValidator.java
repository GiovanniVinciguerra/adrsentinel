package dev.vinciguerra.adrsentinel.web.annotation.driver.validator;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import dev.vinciguerra.adrsentinel.web.annotation.driver.ValidatorLicense;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class LicenseValidator implements ConstraintValidator<ValidatorLicense, String> {
	private static final Map<String, Pattern> LICENSE_PATTERNS = new HashMap<String, Pattern>();

    static {
        // --- STATI MEMBRI DELL'UNIONE EUROPEA ---
        // Austria (AT): 8 cifre
        LICENSE_PATTERNS.put("AT", Pattern.compile("^\\d{8}$"));
        // Belgio (BE): 10 cifre
        LICENSE_PATTERNS.put("BE", Pattern.compile("^\\d{10}$"));
        // Bulgaria (BG): 9 cifre
        LICENSE_PATTERNS.put("BG", Pattern.compile("^\\d{9}$"));
        // Cipro (CY): 12 caratteri alfanumerici
        LICENSE_PATTERNS.put("CY", Pattern.compile("^[A-Z0-9]{12}$"));
        // Croazia (HR): 8 cifre
        LICENSE_PATTERNS.put("HR", Pattern.compile("^\\d{8}$"));
        // Danimarca (DK): 8 cifre
        LICENSE_PATTERNS.put("DK", Pattern.compile("^\\d{8}$"));
        // Estonia (EE): 2 lettere + 6 cifre
        LICENSE_PATTERNS.put("EE", Pattern.compile("^[A-Z]{2}\\d{6}$"));
        // Finlandia (FI): 6 cifre + 4 caratteri alfanumerici (basato sull'ID nazionale)
        LICENSE_PATTERNS.put("FI", Pattern.compile("^\\d{6}[A-Z0-9]{4}$"));
        // Francia (FR): Formato molto flessibile tra vecchio e nuovo registro (da 1 a 15 alfanumerici)
        LICENSE_PATTERNS.put("FR", Pattern.compile("^[A-Z0-9]{1,15}$"));
        // Germania (DE): 11 caratteri alfanumerici (Fahrerlaubnisnummer)
        LICENSE_PATTERNS.put("DE", Pattern.compile("^[A-Z0-9]{11}$"));
        // Grecia (GR): 9 cifre
        LICENSE_PATTERNS.put("GR", Pattern.compile("^\\d{9}$"));
        // Irlanda (IE): 9 caratteri alfanumerici
        LICENSE_PATTERNS.put("IE", Pattern.compile("^[A-Z0-9]{9}$"));
        // Italia (IT): 1 o 2 lettere + 7 cifre + 1 lettera finale
        LICENSE_PATTERNS.put("IT", Pattern.compile("^[A-Z]{1,2}\\d{7}[A-Z]$"));
        // Lettonia (LV): 2 lettere + 6 cifre
        LICENSE_PATTERNS.put("LV", Pattern.compile("^[A-Z]{2}\\d{6}$"));
        // Lituania (LT): 8 cifre
        LICENSE_PATTERNS.put("LT", Pattern.compile("^\\d{8}$"));
        // Lussemburgo (LU): 6 cifre
        LICENSE_PATTERNS.put("LU", Pattern.compile("^\\d{6}$"));
        // Malta (MT): Da 6 a 8 caratteri alfanumerici
        LICENSE_PATTERNS.put("MT", Pattern.compile("^[A-Z0-9]{6,8}$"));
        // Paesi Bassi (NL): 10 cifre
        LICENSE_PATTERNS.put("NL", Pattern.compile("^\\d{10}$"));
        // Polonia (PL): Da 7 a 12 alfanumerici (ripulito dallo slash "/" nativo)
        LICENSE_PATTERNS.put("PL", Pattern.compile("^[A-Z0-9]{7,12}$"));
        // Portogallo (PT): Lettera iniziale + cifre (ripulito dal trattino "-")
        LICENSE_PATTERNS.put("PT", Pattern.compile("^[A-Z0-9]{7,12}$"));
        // Repubblica Ceca (CZ): 2 lettere + 6 cifre
        LICENSE_PATTERNS.put("CZ", Pattern.compile("^[A-Z]{2}\\d{6}$"));
        // Romania (RO): 9 caratteri alfanumerici
        LICENSE_PATTERNS.put("RO", Pattern.compile("^[A-Z0-9]{9}$"));
        // Slovacchia (SK): 1 lettera + 7 cifre
        LICENSE_PATTERNS.put("SK", Pattern.compile("^[A-Z]\\d{7}$"));
        // Slovenia (SI): Da 8 a 9 cifre
        LICENSE_PATTERNS.put("SI", Pattern.compile("^\\d{8,9}$"));
        // Spagna (ES): 8 cifre + 1 lettera finale (spesso identico al documento DNI)
        LICENSE_PATTERNS.put("ES", Pattern.compile("^\\d{8}[A-Z]$"));
        // Svezia (SE): 10 cifre (ricalca il Personnummer, senza trattino)
        LICENSE_PATTERNS.put("SE", Pattern.compile("^\\d{10}$"));
        // Ungheria (HU): 2 lettere + 7 cifre
        LICENSE_PATTERNS.put("HU", Pattern.compile("^[A-Z]{2}\\d{7}$"));

        // --- PAESI EUROPEI EXTRA-UE / SCHENGEN ---
        // Regno Unito (GB): 16 caratteri alfanumerici (formato DVLA molto specifico)
        LICENSE_PATTERNS.put("GB", Pattern.compile("^[A-Z9]{5}\\d{6}[A-Z9]{2}\\d[A-Z]{2}$"));
        // Svizzera (CH): 12 cifre
        LICENSE_PATTERNS.put("CH", Pattern.compile("^\\d{12}$"));
        // Norvegia (NO): 11 cifre
        LICENSE_PATTERNS.put("NO", Pattern.compile("^\\d{11}$"));
        // Islanda (IS): 10 cifre
        LICENSE_PATTERNS.put("IS", Pattern.compile("^\\d{10}$"));
    }
	
	@Override
	public boolean isValid(String value, ConstraintValidatorContext context) {
		if(value == null || value.isBlank())
			return false;
		String manipulatedValue = value.replaceAll("[\\s,\\.\\-/_]+", "").toUpperCase();
		if(manipulatedValue.length() < 5 || manipulatedValue.length() > 20)
			return false;
		return LICENSE_PATTERNS.values().stream()
			.anyMatch(pattern -> pattern.matcher(manipulatedValue).matches());
	}
}
