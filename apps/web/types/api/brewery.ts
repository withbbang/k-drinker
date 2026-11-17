export type BreweryResponse = {
  id: number;
  name: string;
  address: string | null;
  phone: string | null;
  homepage: string | null;
  instagramHandle: string | null;
  otherSns: string | null;
  source: string;
  verified: boolean;
};

export type BreweryRequest = {
  name: string;
  address: string | null;
  phone: string | null;
  homepage: string | null;
  instagramHandle: string | null;
  otherSns: string | null;
};
