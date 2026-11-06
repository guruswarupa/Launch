export interface DocSection {
  heading: string;
  content: string;
}

export interface DocPage {
  title: string;
  sections: DocSection[];
}
