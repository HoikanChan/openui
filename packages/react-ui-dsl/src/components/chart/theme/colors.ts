// UCD 颜色盘

const Blue50 = '#2070F3';
const Green50 = '#62B42E';
const Indigo50 = '#715AFB';
const Cyan50 = '#2CB8C9';
const Orange40 = '#F69E39';
const Brand30 = '#5CA2E9';
const Purple60 = '#8A21BC';
const Rose60 = '#C40054';
const Indigo60 = '#5531EB';
const Yellow60 = '#D19F00';
const Brand60 = '#004EA8';
const Green60 = '#488E20';
const Purple70 = '#651B8B';
const Cyan60 = '#1C94A4';
const Orange70 = '#954304';
const Cyan70 = '#127180';
const Blue60 = '#1F55B5';
const Green70 = '#316614';
const Indigo70 = '#281675';
const Rose70 = '#811439';
const Cyan30 = '#7DDFE7';
const Yellow70 = '#9E7400';
const Purple50 = '#B62BF7';
const Mint70 = '#036142';
export const Gray10 = '#EEEEEE';

// 图表内置的颜色组
const colorGroup = [
  Blue50,
  Green50,
  Indigo50,
  Cyan50,
  Orange40,
  Brand30,
  Purple60,
  Rose60,
  Indigo60,
  Yellow60,
  Brand60,
  Green60,
  Purple70,
  Cyan60,
  Orange70,
  Cyan70,
  Blue60,
  Green70,
  Indigo70,
  Rose70,
  Cyan30,
  Yellow70,
  Purple50,
  Mint70,
];

/**
 * 十六进制转rgba，如 codeToRGB("#6d8ff0", 0.5) --> 'rgba(109,143,240,0.5)'
 */
export function codeToRGB(code: string, opacity: number): string {
  if (code === undefined) {
    return "";
  }
  const result = [
    parseInt(code.substring(1, 3), 16),
    parseInt(code.substring(3, 5), 16),
    parseInt(code.substring(5, 7), 16),
  ];
  return `rgba(${result.join(',')},${opacity})`;
}

export default colorGroup;
