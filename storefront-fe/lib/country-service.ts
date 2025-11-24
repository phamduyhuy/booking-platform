import { apiClient } from './api-client';

export interface Country {
    name: {
        common: string;
        official: string;
    };
    cca2: string; // 2-letter country code
    cca3: string; // 3-letter country code
    flags: {
        svg: string;
        png: string;
    };
    idd: {
        root: string;
        suffixes: string[];
    };
}

class CountryService {
    private countries: Country[] | null = null;
    private fetchPromise: Promise<Country[]> | null = null;

    async getAllCountries(): Promise<Country[]> {
        // Return cached data if available
        if (this.countries) {
            return this.countries;
        }

        // Return existing fetch promise if already fetching
        if (this.fetchPromise) {
            return this.fetchPromise;
        }

        // Fetch countries from REST Countries API
        this.fetchPromise = fetch(
            'https://restcountries.com/v3.1/all?fields=name,cca2,cca3,flags,idd'
        )
            .then((response) => {
                if (!response.ok) {
                    throw new Error('Failed to fetch countries');
                }
                return response.json();
            })
            .then((data: Country[]) => {
                // Sort by common name
                this.countries = data.sort((a, b) =>
                    a.name.common.localeCompare(b.name.common)
                );
                this.fetchPromise = null;
                return this.countries;
            })
            .catch((error) => {
                this.fetchPromise = null;
                console.error('Error fetching countries:', error);
                throw error;
            });

        return this.fetchPromise;
    }

    async getCountryByCode(code: string): Promise<Country | undefined> {
        const countries = await this.getAllCountries();
        return countries.find(
            (c) =>
                c.cca2.toLowerCase() === code.toLowerCase() ||
                c.cca3.toLowerCase() === code.toLowerCase() ||
                c.name.common.toLowerCase() === code.toLowerCase()
        );
    }
}

export const countryService = new CountryService();
