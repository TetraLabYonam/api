enum UnitType {
  publicInterest('PUBLIC_INTEREST', '공익형'),
  market('MARKET', '시장형'),
  socialService('SOCIAL_SERVICE', '사회서비스형');

  final String apiValue;
  final String label;

  const UnitType(this.apiValue, this.label);
}
